import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ClaudeContentBlock, ClaudeMessage, ToolResultBlock } from '../../types';
import { extractMarkdownContent } from '../../utils/copyUtils';
import { MessageItem } from './MessageItem';

vi.mock('../MarkdownBlock', () => ({
  default: ({ content }: { content: string }) => <div data-testid="markdown-block">{content}</div>,
}));

vi.mock('../toolBlocks', () => ({
  ReadToolBlock: () => <div data-testid="read-tool-block">read</div>,
  ReadToolGroupBlock: () => <div data-testid="read-tool-group-block">read-group</div>,
  EditToolBlock: () => <div data-testid="edit-tool-block">edit</div>,
  EditToolGroupBlock: () => <div data-testid="edit-tool-group-block">edit-group</div>,
  BashToolBlock: () => <div data-testid="bash-tool-block">bash</div>,
  BashToolGroupBlock: () => <div data-testid="bash-tool-group-block">bash-group</div>,
  SearchToolGroupBlock: () => <div data-testid="search-tool-group-block">search-group</div>,
}));

vi.mock('./ContentBlockRenderer', () => ({
  ContentBlockRenderer: ({ block }: { block: ClaudeContentBlock }) => (
    <div data-testid={`content-block-${block.type}`}>{block.type}</div>
  ),
}));

vi.mock('./ProviderNotConfiguredCard', () => ({
  ProviderNotConfiguredCard: () => <div data-testid="provider-not-configured-card">provider-card</div>,
  isProviderNotConfiguredError: () => false,
}));

const t = ((key: string) => {
  const translations: Record<string, string> = {
    'markdown.copyMessage': '复制消息',
    'markdown.copySuccess': '已复制',
    'chat.streamingConnected': '已连接',
    'chat.totalDuration': '本次耗时',
    'chat.forkMessage': 'Fork from here',
  };
  return translations[key] ?? key;
}) as any;

const getMessageText = (message: ClaudeMessage) => message.content ?? '';

const getContentBlocks = (message: ClaudeMessage): ClaudeContentBlock[] => {
  const raw = message.raw;
  if (!raw || typeof raw !== 'object') {
    return [];
  }

  const content = Array.isArray(raw.content)
    ? raw.content
    : Array.isArray(raw.message?.content)
      ? raw.message.content
      : [];

  return content as ClaudeContentBlock[];
};

const findToolResult = (_toolId: string | undefined, _messageIndex: number): ToolResultBlock | null => null;

function renderMessageItem(message: ClaudeMessage) {
  return render(
    <MessageItem
      message={message}
      messageIndex={0}
      messageKey="message-0"
      isLast={false}
      streamingActive={false}
      isThinking={false}
      t={t}
      getMessageText={getMessageText}
      getContentBlocks={getContentBlocks}
      findToolResult={findToolResult}
      extractMarkdownContent={extractMarkdownContent}
    />
  );
}

describe('MessageItem copy button visibility', () => {
  it('hides the assistant copy button for tool-only messages', () => {
    const message: ClaudeMessage = {
      type: 'assistant',
      raw: {
        content: [
          {
            type: 'tool_use',
            id: 'tool-1',
            name: 'shell_command',
            input: { cmd: 'git status' },
          },
        ],
      } as any,
    };

    renderMessageItem(message);

    expect(screen.getByTestId('bash-tool-block')).toBeTruthy();
    expect(screen.queryByRole('button', { name: '复制消息' })).toBeNull();
  });

  it('keeps the assistant copy button when tool output is followed by reply text', () => {
    const message: ClaudeMessage = {
      type: 'assistant',
      raw: {
        content: [
          {
            type: 'tool_use',
            id: 'tool-1',
            name: 'shell_command',
            input: { cmd: 'git status' },
          },
          {
            type: 'text',
            text: '提交完成。',
          },
        ],
      } as any,
    };

    renderMessageItem(message);

    expect(screen.getByTestId('bash-tool-block')).toBeTruthy();
    expect(screen.getByTestId('content-block-text')).toBeTruthy();
    expect(screen.getByRole('button', { name: '复制消息' })).toBeTruthy();
  });

  it('groups consecutive exec_command blocks into the batch command tool block', () => {
    const message: ClaudeMessage = {
      type: 'assistant',
      raw: {
        content: [
          {
            type: 'tool_use',
            id: 'tool-1',
            name: 'exec_command',
            input: { command: 'git status' },
          },
          {
            type: 'tool_use',
            id: 'tool-2',
            name: 'exec_command',
            input: { command: 'git diff --cached' },
          },
        ],
      } as any,
    };

    renderMessageItem(message);

    expect(screen.getByTestId('bash-tool-group-block')).toBeTruthy();
    expect(screen.queryAllByTestId('content-block-tool_use')).toHaveLength(0);
  });
});

describe('MessageItem fork button', () => {
  it('renders fork button for eligible user messages and emits the callback', () => {
    const onForkMessage = vi.fn();
    render(
      <MessageItem
        message={{
          type: 'user',
          content: 'Fork me',
          timestamp: '2026-04-21T10:00:00.000Z',
          raw: {
            uuid: 'uuid-user-fork',
            message: { content: [{ type: 'text', text: 'Fork me' }] },
          } as any,
        }}
        messageIndex={3}
        messageKey="uuid-user-fork"
        isLast={false}
        streamingActive={false}
        isThinking={false}
        t={t}
        getMessageText={getMessageText}
        getContentBlocks={getContentBlocks}
        findToolResult={findToolResult}
        extractMarkdownContent={extractMarkdownContent}
        onForkMessage={onForkMessage}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Fork from here' }));

    expect(onForkMessage).toHaveBeenCalledWith('uuid-user-fork', 3);
  });

  it('does not render fork button for streaming assistant placeholders', () => {
    render(
      <MessageItem
        message={{
          type: 'assistant',
          content: '',
          timestamp: '2026-04-21T10:00:00.000Z',
          raw: { uuid: 'uuid-assistant-stream', message: { content: [] } } as any,
        }}
        messageIndex={4}
        messageKey="uuid-assistant-stream"
        isLast={true}
        streamingActive={true}
        isThinking={false}
        t={t}
        getMessageText={getMessageText}
        getContentBlocks={getContentBlocks}
        findToolResult={findToolResult}
        extractMarkdownContent={extractMarkdownContent}
        onForkMessage={vi.fn()}
      />
    );

    expect(screen.queryByRole('button', { name: 'Fork from here' })).toBeNull();
  });

  it('renders fork button for completed assistant messages', () => {
    const onForkMessage = vi.fn();
    render(
      <MessageItem
        message={{
          type: 'assistant',
          content: 'Done',
          timestamp: '2026-04-21T10:00:00.000Z',
          raw: {
            uuid: 'uuid-assistant-done',
            message: { content: [{ type: 'text', text: 'Done' }] },
          } as any,
        }}
        messageIndex={5}
        messageKey="uuid-assistant-done"
        isLast={false}
        streamingActive={false}
        isThinking={false}
        t={t}
        getMessageText={getMessageText}
        getContentBlocks={getContentBlocks}
        findToolResult={findToolResult}
        extractMarkdownContent={extractMarkdownContent}
        onForkMessage={onForkMessage}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Fork from here' }));

    expect(onForkMessage).toHaveBeenCalledWith('uuid-assistant-done', 5);
  });
});
