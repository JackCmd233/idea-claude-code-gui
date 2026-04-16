import { useCallback, useRef, type MutableRefObject, type RefObject } from 'react';
import type { TFunction } from 'i18next';
import { sendBridgeEvent } from '../utils/bridge';
import type { ClaudeContentBlock, ClaudeMessage } from '../types';
import type { Attachment, ChatInputBoxHandle, PermissionMode, SelectedAgent } from '../components/ChatInputBox/types';
import type { ViewMode } from './useModelProviderState';
import type { PendingRegenerationState } from './useWindowCallbacks';
import { findLastVisibleAssistantGroupRange } from '../utils/messageUtils';

/**
 * Captured user prompt context that can be replayed for response regeneration.
 */
export interface ReplayablePromptContext {
  text: string;
  attachments?: Attachment[];
  fileTagsInfo?: { displayPath: string; absolutePath: string }[] | null;
}

/**
 * Command sets for local handling (shared with App.tsx to avoid duplication)
 */
export const NEW_SESSION_COMMANDS = new Set(['/new', '/clear', '/reset']);
export const RESUME_COMMANDS = new Set(['/resume', '/continue']);
export const PLAN_COMMANDS = new Set(['/plan']);

const normalizeBlocksForAssistantGrouping = (raw?: ClaudeMessage['raw']) => {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const rawObj = raw as { content?: unknown; message?: { content?: unknown } };
  const blocks = rawObj.content ?? rawObj.message?.content;
  return Array.isArray(blocks) ? (blocks as ClaudeContentBlock[]) : null;
};

export interface UseMessageSenderOptions {
  t: TFunction;
  addToast: (message: string, type?: 'info' | 'success' | 'warning' | 'error') => void;
  currentProvider: string;
  selectedModel: string;
  permissionMode: PermissionMode;
  reasoningEffort: string;
  selectedAgent: SelectedAgent | null;
  sdkStatusLoaded: boolean;
  currentSdkInstalled: boolean;
  sentAttachmentsRef: RefObject<Map<string, Array<{ fileName: string; mediaType: string }>>>;
  chatInputRef: RefObject<ChatInputBoxHandle | null>;
  messagesContainerRef: RefObject<HTMLDivElement | null>;
  isUserAtBottomRef: RefObject<boolean>;
  userPausedRef: RefObject<boolean>;
  isStreamingRef: RefObject<boolean>;
  pendingRegenerationRef: MutableRefObject<PendingRegenerationState | null>;
  setMessages: React.Dispatch<React.SetStateAction<ClaudeMessage[]>>;
  setLoading: React.Dispatch<React.SetStateAction<boolean>>;
  setLoadingStartTime: React.Dispatch<React.SetStateAction<number | null>>;
  setStreamingActive: React.Dispatch<React.SetStateAction<boolean>>;
  setSettingsInitialTab: React.Dispatch<React.SetStateAction<any>>;
  setCurrentView: React.Dispatch<React.SetStateAction<ViewMode>>;
  forceCreateNewSession: () => void;
  handleModeSelect?: (mode: PermissionMode) => void;
}

/**
 * Handles message building, validation, and sending to the backend.
 */
export function useMessageSender({
  t,
  addToast,
  currentProvider,
  selectedModel,
  permissionMode,
  reasoningEffort,
  selectedAgent,
  sdkStatusLoaded,
  currentSdkInstalled,
  sentAttachmentsRef,
  chatInputRef,
  messagesContainerRef,
  isUserAtBottomRef,
  userPausedRef,
  isStreamingRef,
  pendingRegenerationRef,
  setMessages,
  setLoading,
  setLoadingStartTime,
  setStreamingActive,
  setSettingsInitialTab,
  setCurrentView,
  forceCreateNewSession,
  handleModeSelect,
}: UseMessageSenderOptions) {
  /** Stores the last user prompt context for regeneration */
  const lastReplayablePromptContextRef = useRef<ReplayablePromptContext | null>(null);

  /**
   * Check if the input is a new session command
   */
  const checkNewSessionCommand = useCallback((text: string): boolean => {
    if (!text.startsWith('/')) return false;
    const command = text.split(/\s+/)[0].toLowerCase();
    if (NEW_SESSION_COMMANDS.has(command)) {
      forceCreateNewSession();
      return true;
    }
    return false;
  }, [forceCreateNewSession]);

  /**
   * Check for local-handled slash commands (/resume, /plan)
   * Returns true if the command was handled locally
   * Note: This is also checked in App.tsx handleSubmit to bypass loading queue
   */
  const checkLocalCommand = useCallback((text: string): boolean => {
    if (!text.startsWith('/')) return false;
    const command = text.split(/\s+/)[0].toLowerCase();

    // /resume - open history view
    if (RESUME_COMMANDS.has(command)) {
      setCurrentView('history');
      return true;
    }

    // /plan - switch to plan mode (Claude only)
    if (PLAN_COMMANDS.has(command)) {
      if (currentProvider === 'codex') {
        addToast(t('chat.planModeNotAvailableForCodex', { defaultValue: 'Plan mode is not available for Codex provider' }), 'warning');
      } else if (handleModeSelect) {
        handleModeSelect('plan');
        addToast(t('chat.planModeEnabled', { defaultValue: 'Plan mode enabled' }), 'info');
      }
      return true;
    }

    return false;
  }, [setCurrentView, handleModeSelect, currentProvider, addToast, t]);

  /**
   * Check for unimplemented slash commands
   */
  const checkUnimplementedCommand = useCallback((text: string): boolean => {
    if (!text.startsWith('/')) return false;

    const command = text.split(/\s+/)[0].toLowerCase();
    const unimplementedCommands = ['/plugin', '/plugins'];

    if (unimplementedCommands.includes(command)) {
      const userMessage: ClaudeMessage = {
        type: 'user',
        content: text,
        timestamp: new Date().toISOString(),
      };
      const assistantMessage: ClaudeMessage = {
        type: 'assistant',
        content: t('chat.commandNotImplemented', { command }),
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, userMessage, assistantMessage]);
      return true;
    }
    return false;
  }, [t, setMessages]);

  /**
   * Build content blocks for the user message
   */
  const buildUserContentBlocks = useCallback((
    text: string,
    attachments: Attachment[] | undefined
  ): ClaudeContentBlock[] => {
    const blocks: ClaudeContentBlock[] = [];

    const hasImageAttachments = Array.isArray(attachments) &&
      attachments.some(att => att.mediaType?.startsWith('image/'));

    if (Array.isArray(attachments) && attachments.length > 0) {
      for (const att of attachments) {
        if (att.mediaType?.startsWith('image/')) {
          blocks.push({
            type: 'image',
            src: `data:${att.mediaType};base64,${att.data}`,
            mediaType: att.mediaType,
          });
        } else {
          blocks.push({
            type: 'attachment',
            fileName: att.fileName,
            mediaType: att.mediaType,
          });
        }
      }
    }

    // Filter placeholder text: skip if there are image attachments and text is placeholder
    const isPlaceholderText = text && text.trim().startsWith('[Uploaded ');

    if (text && !(hasImageAttachments && isPlaceholderText)) {
      blocks.push({ type: 'text', text });
    }

    return blocks;
  }, []);

  /**
   * Send message to backend
   */
  const sendMessageToBackend = useCallback((
    text: string,
    attachments: Attachment[] | undefined,
    agentInfo: { id: string; name: string; prompt?: string } | null,
    fileTagsInfo: { displayPath: string; absolutePath: string }[] | null,
    requestedPermissionMode: PermissionMode
  ) => {
    const hasAttachments = Array.isArray(attachments) && attachments.length > 0;
    const effectivePermissionMode: PermissionMode = currentProvider === 'codex' && requestedPermissionMode === 'plan'
      ? 'default'
      : requestedPermissionMode;
    console.debug('[ModeSync][Frontend] send request mode', {
      provider: currentProvider,
      requestedMode: requestedPermissionMode,
      effectiveMode: effectivePermissionMode,
    });

    if (hasAttachments) {
      try {
        const payload = JSON.stringify({
          text,
          attachments: (attachments || []).map(a => ({
            fileName: a.fileName,
            mediaType: a.mediaType,
            data: a.data,
          })),
          agent: agentInfo,
          fileTags: fileTagsInfo,
          permissionMode: effectivePermissionMode,
        });
        sendBridgeEvent('send_message_with_attachments', payload);
      } catch (error) {
        console.error('[Frontend] Failed to serialize attachments payload', error);
        const fallbackPayload = JSON.stringify({
          text,
          agent: agentInfo,
          fileTags: fileTagsInfo,
          permissionMode: effectivePermissionMode,
        });
        sendBridgeEvent('send_message', fallbackPayload);
      }
    } else {
      const payload = JSON.stringify({
        text,
        agent: agentInfo,
        fileTags: fileTagsInfo,
        permissionMode: effectivePermissionMode,
      });
      sendBridgeEvent('send_message', payload);
    }
  }, [currentProvider]);

  /**
   * Execute message sending (from queue or directly)
   */
  const executeMessage = useCallback((content: string, attachments?: Attachment[]) => {
    const text = content.replace(/[\u200B-\u200D\uFEFF]/g, '').trim();
    const hasAttachments = Array.isArray(attachments) && attachments.length > 0;

    if (!text && !hasAttachments) return;

    // Check SDK status
    if (!sdkStatusLoaded) {
      addToast(t('chat.sdkStatusLoading'), 'info');
      return;
    }
    if (!currentSdkInstalled) {
      addToast(
        t('chat.sdkNotInstalled', { provider: currentProvider === 'codex' ? 'Codex' : 'Claude Code' }) + ' ' + t('chat.goInstallSdk'),
        'warning'
      );
      setSettingsInitialTab('dependencies');
      setCurrentView('settings');
      return;
    }

    // Build user message content blocks
    const userContentBlocks = buildUserContentBlocks(text, attachments);
    if (userContentBlocks.length === 0) return;

    // Persist non-image attachment metadata
    const nonImageAttachments = Array.isArray(attachments)
      ? attachments.filter(a => !a.mediaType?.startsWith('image/'))
      : [];
    if (nonImageAttachments.length > 0) {
      const MAX_ATTACHMENT_CACHE_SIZE = 100;
      if (sentAttachmentsRef.current.size >= MAX_ATTACHMENT_CACHE_SIZE) {
        const firstKey = sentAttachmentsRef.current.keys().next().value;
        if (firstKey !== undefined) {
          sentAttachmentsRef.current.delete(firstKey);
        }
      }
      sentAttachmentsRef.current.set(text || '', nonImageAttachments.map(a => ({
        fileName: a.fileName,
        mediaType: a.mediaType,
      })));
    }

    // Create and add user message (optimistic update)
    const userMessage: ClaudeMessage = {
      type: 'user',
      content: text || '',
      timestamp: new Date().toISOString(),
      isOptimistic: true,
      raw: { message: { content: userContentBlocks } },
    };
    setMessages((prev) => [...prev, userMessage]);

    // Set loading state
    setLoading(true);
    setLoadingStartTime(Date.now());

    // Scroll to bottom
    userPausedRef.current = false;
    isUserAtBottomRef.current = true;
    requestAnimationFrame(() => {
      if (messagesContainerRef.current) {
        messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
      }
    });

    // Sync provider setting
    sendBridgeEvent('set_provider', currentProvider);

    // Build agent info
    const agentInfo = selectedAgent ? {
      id: selectedAgent.id,
      name: selectedAgent.name,
      prompt: selectedAgent.prompt,
    } : null;

    // Extract file tag info
    const fileTags = chatInputRef.current?.getFileTags() ?? [];
    const fileTagsInfo = fileTags.length > 0 ? fileTags.map(tag => ({
      displayPath: tag.displayPath,
      absolutePath: tag.absolutePath,
    })) : null;

    // Capture replayable prompt context for regeneration
    lastReplayablePromptContextRef.current = {
      text,
      attachments,
      fileTagsInfo,
    };

    // Send message to backend
    sendMessageToBackend(text, attachments, agentInfo, fileTagsInfo, permissionMode);
  }, [
    sdkStatusLoaded,
    currentSdkInstalled,
    currentProvider,
    permissionMode,
    selectedAgent,
    buildUserContentBlocks,
    sendMessageToBackend,
    addToast,
    t,
  ]);

  /**
   * Handle message submission (from ChatInputBox)
   */
  const handleSubmit = useCallback((content: string, attachments?: Attachment[]) => {
    const text = content.replace(/[\u200B-\u200D\uFEFF]/g, '').trim();
    const hasAttachments = Array.isArray(attachments) && attachments.length > 0;

    if (!text && !hasAttachments) return;

    // Check new session commands
    if (checkNewSessionCommand(text)) return;

    // Check local-handled commands (/resume, /plan)
    if (checkLocalCommand(text)) return;

    // Check for unimplemented commands
    if (checkUnimplementedCommand(text)) return;

    // Execute message
    executeMessage(content, attachments);
  }, [checkNewSessionCommand, checkLocalCommand, checkUnimplementedCommand, executeMessage]);

  /**
   * Interrupt the current session
   */
  const interruptSession = useCallback(() => {
    setLoading(false);
    setLoadingStartTime(null);
    setStreamingActive(false);
    isStreamingRef.current = false;

    sendBridgeEvent('interrupt_session');
  }, []);

  /**
   * Regenerate the last assistant response in place.
   * Clears the last assistant message and re-sends using the stored prompt context
   * with the current runtime configuration (provider, model, mode, reasoning, agent).
   */
  const regenerateLastAssistant = useCallback(() => {
    const replayable = lastReplayablePromptContextRef.current;
    if (!replayable) return;

    let replaced = false;
    let placeholderTimestamp: string | null = null;

    // Clear the last visible assistant group and mark it as streaming
    setMessages((prev) => {
      const groupRange = findLastVisibleAssistantGroupRange(prev, normalizeBlocksForAssistantGrouping);
      if (!groupRange) return prev;

      const anchorMessage = prev[groupRange.startIndex];
      const anchorRaw =
        typeof anchorMessage.raw === 'object' && anchorMessage.raw
          ? (anchorMessage.raw as Record<string, unknown>)
          : null;
      placeholderTimestamp = anchorMessage.timestamp ?? new Date().toISOString();
      const placeholderRaw = anchorRaw
        ? {
            ...anchorRaw,
            content: [],
            message: {
              ...(typeof anchorRaw.message === 'object' && anchorRaw.message
                ? (anchorRaw.message as Record<string, unknown>)
                : {}),
              content: [],
            },
          }
        : { message: { content: [] } };

      replaced = true;
      return [
        ...prev.slice(0, groupRange.startIndex),
        {
          type: 'assistant',
          content: '',
          timestamp: placeholderTimestamp,
          isStreaming: true,
          raw: placeholderRaw as ClaudeMessage['raw'],
        },
        ...prev.slice(groupRange.endIndex + 1),
      ];
    });

    if (!replaced || !placeholderTimestamp) {
      return;
    }

    pendingRegenerationRef.current = { placeholderTimestamp };

    // Match the normal send path: only loading starts here.
    // The global streaming state must be driven by backend stream callbacks,
    // otherwise showLoading(false) cleanup can be suppressed indefinitely.
    setLoading(true);
    setLoadingStartTime(Date.now());

    // Sync current runtime configuration
    sendBridgeEvent('set_provider', currentProvider);
    sendBridgeEvent('set_model', selectedModel);
    const effectivePermissionMode = currentProvider === 'codex' && permissionMode === 'plan'
      ? 'default'
      : permissionMode;
    sendBridgeEvent('set_mode', effectivePermissionMode);
    sendBridgeEvent('set_reasoning_effort', reasoningEffort);
    sendBridgeEvent(
      'set_selected_agent',
      selectedAgent
        ? JSON.stringify({ id: selectedAgent.id, name: selectedAgent.name, prompt: selectedAgent.prompt })
        : ''
    );

    // Build agent info and re-send
    const agentInfo = selectedAgent
      ? { id: selectedAgent.id, name: selectedAgent.name, prompt: selectedAgent.prompt }
      : null;

    sendMessageToBackend(
      replayable.text,
      replayable.attachments,
      agentInfo,
      replayable.fileTagsInfo ?? null,
      permissionMode
    );
  }, [
    currentProvider,
    pendingRegenerationRef,
    selectedModel,
    permissionMode,
    reasoningEffort,
    selectedAgent,
    sendMessageToBackend,
  ]);

  return {
    handleSubmit,
    executeMessage,
    interruptSession,
    lastReplayablePromptContextRef,
    regenerateLastAssistant,
  };
}
