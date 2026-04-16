import { renderHook, act } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { UseMessageSenderOptions } from './useMessageSender';
import { useMessageSender } from './useMessageSender';
import * as bridge from '../utils/bridge';

vi.mock('../utils/bridge', () => ({
  sendBridgeEvent: vi.fn(),
}));

const t = ((key: string) => key) as any;

function createDefaultOptions(overrides?: Partial<UseMessageSenderOptions>): UseMessageSenderOptions {
  return {
    t,
    addToast: vi.fn(),
    currentProvider: 'claude',
    permissionMode: 'default',
    selectedModel: 'claude-sonnet-4-5',
    reasoningEffort: 'medium',
    selectedAgent: null,
    sdkStatusLoaded: true,
    currentSdkInstalled: true,
    sentAttachmentsRef: { current: new Map() },
    chatInputRef: { current: { getFileTags: () => [] } } as any,
    messagesContainerRef: { current: null },
    isUserAtBottomRef: { current: true },
    userPausedRef: { current: false },
    isStreamingRef: { current: false },
    pendingRegenerationRef: { current: null },
    setMessages: vi.fn((updater) => updater([])),
    setLoading: vi.fn(),
    setLoadingStartTime: vi.fn(),
    setStreamingActive: vi.fn(),
    setSettingsInitialTab: vi.fn(),
    setCurrentView: vi.fn(),
    forceCreateNewSession: vi.fn(),
    ...overrides,
  };
}

describe('useMessageSender replayable prompt context', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('stores replayable prompt context from the last submitted user message', () => {
    const options = createDefaultOptions({
      chatInputRef: {
        current: {
          getFileTags: () => [
            { displayPath: 'src/App.tsx', absolutePath: 'D:/Code/webview/src/App.tsx' },
          ],
        },
      } as any,
    });

    const { result } = renderHook(() => useMessageSender(options));

    act(() => {
      result.current.executeMessage('Explain this diff');
    });

    expect(result.current.lastReplayablePromptContextRef.current).toEqual({
      text: 'Explain this diff',
      attachments: undefined,
      fileTagsInfo: [
        {
          displayPath: 'src/App.tsx',
          absolutePath: 'D:/Code/webview/src/App.tsx',
        },
      ],
    });
  });

  it('stores null fileTagsInfo when no file tags present', () => {
    const options = createDefaultOptions();

    const { result } = renderHook(() => useMessageSender(options));

    act(() => {
      result.current.executeMessage('Hello');
    });

    expect(result.current.lastReplayablePromptContextRef.current).toEqual({
      text: 'Hello',
      attachments: undefined,
      fileTagsInfo: null,
    });
  });
});

describe('useMessageSender regenerateLastAssistant', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('clears the last assistant and sends with current runtime config', () => {
    let currentMessages = [
      { type: 'user', content: 'Explain this diff', timestamp: '2026-04-15T00:00:00.000Z' },
      { type: 'assistant', content: 'Old answer', timestamp: '2026-04-15T00:00:01.000Z', durationMs: 1200 },
    ];
    const setMessages = vi.fn((updater: any) => {
      currentMessages = updater(currentMessages);
      return currentMessages;
    });

    const options = createDefaultOptions({
      currentProvider: 'codex',
      selectedModel: 'gpt-5.2',
      permissionMode: 'default',
      reasoningEffort: 'high',
      selectedAgent: { id: 'agent-2', name: 'Reviewer', prompt: 'Review every patch.' },
      setMessages,
      isStreamingRef: { current: false },
    });

    const { result } = renderHook(() => useMessageSender(options));

    act(() => {
      result.current.lastReplayablePromptContextRef.current = {
        text: 'Explain this diff',
        attachments: undefined,
        fileTagsInfo: null,
      };
      result.current.regenerateLastAssistant();
    });

    // Last assistant should be cleared and marked streaming
    expect(currentMessages[currentMessages.length - 1]).toMatchObject({
      type: 'assistant',
      content: '',
      isStreaming: true,
    });

    const sendBridgeEventSpy = bridge.sendBridgeEvent as ReturnType<typeof vi.fn>;
    expect(sendBridgeEventSpy).toHaveBeenCalledWith('set_provider', 'codex');
    expect(sendBridgeEventSpy).toHaveBeenCalledWith('set_model', 'gpt-5.2');
    expect(sendBridgeEventSpy).toHaveBeenCalledWith('set_mode', 'default');
    expect(sendBridgeEventSpy).toHaveBeenCalledWith('set_reasoning_effort', 'high');
    expect(sendBridgeEventSpy).toHaveBeenCalledWith(
      'set_selected_agent',
      JSON.stringify({ id: 'agent-2', name: 'Reviewer', prompt: 'Review every patch.' })
    );
  });

  it('does nothing when no replayable context exists', () => {
    const setMessages = vi.fn();
    const options = createDefaultOptions({ setMessages });

    const { result } = renderHook(() => useMessageSender(options));

    act(() => {
      result.current.regenerateLastAssistant();
    });

    expect(setMessages).not.toHaveBeenCalled();
  });

  it('does not mark global streaming active before backend stream callbacks arrive', () => {
    let currentMessages = [
      { type: 'user', content: 'Explain this diff', timestamp: '2026-04-15T00:00:00.000Z' },
      { type: 'assistant', content: 'Old answer', timestamp: '2026-04-15T00:00:01.000Z' },
    ];
    const setMessages = vi.fn((updater: any) => {
      currentMessages = updater(currentMessages);
      return currentMessages;
    });
    const setStreamingActive = vi.fn();
    const isStreamingRef = { current: false };
    const options = createDefaultOptions({
      setMessages,
      setStreamingActive,
      isStreamingRef,
    });

    const { result } = renderHook(() => useMessageSender(options));

    act(() => {
      result.current.lastReplayablePromptContextRef.current = {
        text: 'Explain this diff',
        attachments: undefined,
        fileTagsInfo: null,
      };
      result.current.regenerateLastAssistant();
    });

    expect(setStreamingActive).not.toHaveBeenCalled();
    expect(isStreamingRef.current).toBe(false);
    expect(currentMessages[currentMessages.length - 1]).toMatchObject({
      type: 'assistant',
      content: '',
      isStreaming: true,
    });
  });

  it('replaces the full last visible assistant group with a single empty streaming placeholder', () => {
    let currentMessages: any[] = [
      { type: 'user', content: 'Explain this diff', timestamp: '2026-04-15T00:00:00.000Z' },
      {
        type: 'assistant',
        content: '',
        timestamp: '2026-04-15T00:00:01.000Z',
        raw: { content: [{ type: 'tool_use', id: 'tool-1', name: 'shell_command', input: { command: 'git diff' } }] },
      },
      {
        type: 'user',
        content: '[tool_result]',
        timestamp: '2026-04-15T00:00:02.000Z',
        raw: { content: [{ type: 'tool_result', tool_use_id: 'tool-1', content: 'ok' }] },
      },
      {
        type: 'assistant',
        content: 'Old visible answer',
        timestamp: '2026-04-15T00:00:03.000Z',
        raw: { content: [{ type: 'text', text: 'Old visible answer' }] },
      },
    ];
    const setMessages = vi.fn((updater: any) => {
      currentMessages = updater(currentMessages);
      return currentMessages;
    });

    const options = createDefaultOptions({ setMessages });
    const { result } = renderHook(() => useMessageSender(options));

    act(() => {
      result.current.lastReplayablePromptContextRef.current = {
        text: 'Explain this diff',
        attachments: undefined,
        fileTagsInfo: null,
      };
      result.current.regenerateLastAssistant();
    });

    expect(currentMessages).toEqual([
      { type: 'user', content: 'Explain this diff', timestamp: '2026-04-15T00:00:00.000Z' },
      expect.objectContaining({
        type: 'assistant',
        content: '',
        isStreaming: true,
        timestamp: '2026-04-15T00:00:01.000Z',
      }),
    ]);
  });
});
