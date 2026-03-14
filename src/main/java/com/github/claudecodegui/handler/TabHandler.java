package com.github.claudecodegui.handler;

import com.github.claudecodegui.ClaudeChatWindow;
import com.github.claudecodegui.ClaudeSDKToolWindow;
import com.github.claudecodegui.settings.GlobalTabStateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;


/**
 * Tab management handler.
 * Handles creating new chat tabs in the tool window.
 */
public class TabHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(TabHandler.class);

    private static final String TOOL_WINDOW_ID = "CCG";
    private static final String TYPE_CREATE_NEW_TAB = "create_new_tab";
    private static final String[] SUPPORTED_TYPES = {TYPE_CREATE_NEW_TAB};

    public TabHandler(@NotNull HandlerContext context) {
        super(context);
    }

    @Override
    public String[] getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean handle(@NotNull String type, @NotNull String content) {
        if (TYPE_CREATE_NEW_TAB.equals(type)) {
            LOG.debug("[TabHandler] Processing create_new_tab");
            handleCreateNewTab();
            return true;
        }
        return false;
    }

    /**
     * Create a new chat tab in the tool window.
     */
    private void handleCreateNewTab() {
        Project project = context.getProject();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
                if (toolWindow == null) {
                    LOG.error("[TabHandler] Tool window not found");
                    callJavaScript("addErrorMessage", escapeJs("无法找到 CCG 工具窗口"));
                    return;
                }

                // Create a new chat window instance with skipRegister=true (don't replace the main instance)
                ClaudeChatWindow newChatWindow = new ClaudeChatWindow(project, true);

                ContentManager contentManager = toolWindow.getContentManager();
                String tabName = ClaudeSDKToolWindow.getNextTabName(toolWindow);

                // Create and add the new tab content
                ContentFactory contentFactory = ContentFactory.getInstance();
                Content content = contentFactory.createContent(newChatWindow.getContent(), tabName, false);
                content.setCloseable(true);
                newChatWindow.setParentContent(content);

                contentManager.addContent(content);
                contentManager.setSelectedContent(content);

                // Create global tab record and associate with the chat window
                GlobalTabStateService.GlobalTabRecord globalTab = GlobalTabStateService.getInstance().createTab(tabName);
                newChatWindow.setGlobalTabId(globalTab.getId());

                // Ensure the tool window is visible
                toolWindow.show(null);

                LOG.info("[TabHandler] Created new tab: " + tabName);
            } catch (Exception e) {
                LOG.error("[TabHandler] Error creating new tab: " + e.getMessage(), e);
                callJavaScript("addErrorMessage", escapeJs("创建新标签页失败: " + e.getMessage()));
            }
        });
    }
}
