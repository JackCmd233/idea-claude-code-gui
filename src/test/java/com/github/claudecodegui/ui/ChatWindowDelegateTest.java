package com.github.claudecodegui.ui;

import com.github.claudecodegui.ClaudeSession;
import com.github.claudecodegui.CodemossSettingsService;
import com.github.claudecodegui.handler.HandlerContext;
import com.github.claudecodegui.handler.HistoryHandler;
import com.github.claudecodegui.handler.MessageDispatcher;
import com.github.claudecodegui.handler.PermissionHandler;
import com.github.claudecodegui.provider.claude.ClaudeSDKBridge;
import com.github.claudecodegui.provider.codex.CodexSDKBridge;
import com.github.claudecodegui.session.SessionLifecycleManager;
import com.github.claudecodegui.session.StreamMessageCoalescer;
import com.github.claudecodegui.util.JsUtils;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.jcef.JBCefBrowser;
import org.junit.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ChatWindowDelegateTest {

    @Test
    public void addExternalSelection_queuesUntilFrontendReady() {
        FakeDelegateHost host = new FakeDelegateHost(false);
        ChatWindowDelegate delegate = new ChatWindowDelegate(host);

        delegate.addExternalSelection("console selection");

        assertTrue(host.getCalls("addCodeSnippet").isEmpty());

        delegate.handleFrontendReady();

        List<JsCall> addCodeSnippetCalls = host.getCalls("addCodeSnippet");
        assertEquals(1, addCodeSnippetCalls.size());
        assertEquals(JsUtils.escapeJs("console selection"), addCodeSnippetCalls.get(0).args[0]);
    }

    @Test
    public void addExternalSelection_sendsImmediatelyWhenFrontendReady() {
        FakeDelegateHost host = new FakeDelegateHost(true);
        ChatWindowDelegate delegate = new ChatWindowDelegate(host);

        delegate.addExternalSelection("terminal selection");

        List<JsCall> addCodeSnippetCalls = host.getCalls("addCodeSnippet");
        assertEquals(1, addCodeSnippetCalls.size());
        assertEquals(JsUtils.escapeJs("terminal selection"), addCodeSnippetCalls.get(0).args[0]);
    }

    @Test
    public void handleFrontendReady_replaysQueuedSelectionsInOrder() {
        FakeDelegateHost host = new FakeDelegateHost(false);
        ChatWindowDelegate delegate = new ChatWindowDelegate(host);

        delegate.addExternalSelection("first");
        delegate.addExternalSelection("second");

        delegate.handleFrontendReady();

        List<JsCall> addCodeSnippetCalls = host.getCalls("addCodeSnippet");
        assertEquals(2, addCodeSnippetCalls.size());
        assertEquals(JsUtils.escapeJs("first"), addCodeSnippetCalls.get(0).args[0]);
        assertEquals(JsUtils.escapeJs("second"), addCodeSnippetCalls.get(1).args[0]);
    }

    @Test
    public void extractQuickFixResponseOrThrow_returnsLastAssistantContent() {
        List<ClaudeSession.Message> messages = new ArrayList<>();
        messages.add(new ClaudeSession.Message(ClaudeSession.Message.Type.USER, "prompt"));
        messages.add(new ClaudeSession.Message(ClaudeSession.Message.Type.ASSISTANT, "fixed code"));

        assertEquals("fixed code", ChatWindowDelegate.extractQuickFixResponseOrThrow(messages));
    }

    @Test
    public void extractQuickFixResponseOrThrow_throwsWhenMessagesEmpty() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ChatWindowDelegate.extractQuickFixResponseOrThrow(new ArrayList<>())
        );

        assertEquals("QuickFix failed: no messages returned", ex.getMessage());
    }

    @Test
    public void extractQuickFixResponseOrThrow_throwsWhenLastMessageNotAssistant() {
        List<ClaudeSession.Message> messages = new ArrayList<>();
        messages.add(new ClaudeSession.Message(ClaudeSession.Message.Type.USER, "prompt"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ChatWindowDelegate.extractQuickFixResponseOrThrow(messages)
        );

        assertEquals("QuickFix failed: last message is not assistant", ex.getMessage());
    }

    @Test
    public void extractQuickFixResponseOrThrow_throwsWhenAssistantContentMissing() {
        List<ClaudeSession.Message> messages = new ArrayList<>();
        messages.add(new ClaudeSession.Message(ClaudeSession.Message.Type.ASSISTANT, null));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ChatWindowDelegate.extractQuickFixResponseOrThrow(messages)
        );

        assertEquals("QuickFix failed: assistant response is empty", ex.getMessage());
    }

    private static final class FakeDelegateHost implements ChatWindowDelegate.DelegateHost {
        private final List<JsCall> calls = new ArrayList<>();
        private boolean frontendReady;
        private final SessionLifecycleManager sessionLifecycleManager;
        private final StreamMessageCoalescer streamMessageCoalescer;

        private FakeDelegateHost(boolean frontendReady) {
            this.frontendReady = frontendReady;
            this.streamMessageCoalescer = new StreamMessageCoalescer(new StreamMessageCoalescer.JsCallbackTarget() {
                @Override
                public void callJavaScript(String functionName, String... args) {
                }

                @Override
                public JBCefBrowser getBrowser() {
                    return null;
                }

                @Override
                public boolean isDisposed() {
                    return false;
                }

                @Override
                public HandlerContext getHandlerContext() {
                    return null;
                }
            });
            this.sessionLifecycleManager = new NoOpSessionLifecycleManager(streamMessageCoalescer);
        }

        private List<JsCall> getCalls(String functionName) {
            List<JsCall> matches = new ArrayList<>();
            for (JsCall call : calls) {
                if (functionName.equals(call.functionName)) {
                    matches.add(call);
                }
            }
            return matches;
        }

        @Override
        public Project getProject() {
            return null;
        }

        @Override
        public ClaudeSDKBridge getClaudeSDKBridge() {
            return null;
        }

        @Override
        public CodexSDKBridge getCodexSDKBridge() {
            return null;
        }

        @Override
        public ClaudeSession getSession() {
            return null;
        }

        @Override
        public CodemossSettingsService getSettingsService() {
            return null;
        }

        @Override
        public JPanel getMainPanel() {
            return new JPanel();
        }

        @Override
        public JBCefBrowser getBrowser() {
            return null;
        }

        @Override
        public boolean isDisposed() {
            return false;
        }

        @Override
        public void callJavaScript(String fn, String... args) {
            calls.add(new JsCall(fn, args));
        }

        @Override
        public Content getParentContent() {
            return null;
        }

        @Override
        public String getOriginalTabName() {
            return null;
        }

        @Override
        public void setOriginalTabName(String name) {
        }

        @Override
        public String getSessionId() {
            return null;
        }

        @Override
        public HandlerContext getHandlerContext() {
            return null;
        }

        @Override
        public void setHandlerContext(HandlerContext ctx) {
        }

        @Override
        public void setMessageDispatcher(MessageDispatcher d) {
        }

        @Override
        public void setPermissionHandler(PermissionHandler h) {
        }

        @Override
        public void setHistoryHandler(HistoryHandler h) {
        }

        @Override
        public SessionLifecycleManager getSessionLifecycleManager() {
            return sessionLifecycleManager;
        }

        @Override
        public StreamMessageCoalescer getStreamCoalescer() {
            return streamMessageCoalescer;
        }

        @Override
        public WebviewWatchdog getWebviewWatchdog() {
            return null;
        }

        @Override
        public PermissionHandler getPermissionHandler() {
            return null;
        }

        @Override
        public void interruptDueToPermissionDenial() {
        }

        @Override
        public boolean isFrontendReady() {
            return frontendReady;
        }

        @Override
        public void setFrontendReady(boolean ready) {
            frontendReady = ready;
        }

        @Override
        public void setSlashCommandsFetched(boolean fetched) {
        }

        @Override
        public void setFetchedSlashCommandsCount(int count) {
        }
    }

    private static final class JsCall {
        private final String functionName;
        private final String[] args;

        private JsCall(String functionName, String[] args) {
            this.functionName = functionName;
            this.args = args;
        }
    }

    private static final class NoOpSessionLifecycleManager extends SessionLifecycleManager {
        private NoOpSessionLifecycleManager(StreamMessageCoalescer streamMessageCoalescer) {
            super(new SessionLifecycleManager.SessionHost() {
                @Override
                public Project getProject() {
                    return null;
                }

                @Override
                public ClaudeSDKBridge getClaudeSDKBridge() {
                    return null;
                }

                @Override
                public CodexSDKBridge getCodexSDKBridge() {
                    return null;
                }

                @Override
                public ClaudeSession getSession() {
                    return null;
                }

                @Override
                public void setSession(ClaudeSession session) {
                }

                @Override
                public HandlerContext getHandlerContext() {
                    return null;
                }

                @Override
                public StreamMessageCoalescer getStreamCoalescer() {
                    return streamMessageCoalescer;
                }

                @Override
                public void clearPendingPermissionRequests() {
                }

                @Override
                public void clearPermissionDecisionMemory() {
                }

                @Override
                public void callJavaScript(String functionName, String... args) {
                }

                @Override
                public boolean isDisposed() {
                    return false;
                }

                @Override
                public JBCefBrowser getBrowser() {
                    return null;
                }

                @Override
                public void setupSessionCallbacks() {
                }

                @Override
                public void invalidateSessionCallbacks() {
                }

                @Override
                public void setSlashCommandsFetched(boolean fetched) {
                }

                @Override
                public void setFetchedSlashCommandsCount(int count) {
                }
            });
        }

        @Override
        public void sendCurrentPermissionMode() {
        }
    }
}
