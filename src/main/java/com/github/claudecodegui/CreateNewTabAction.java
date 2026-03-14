package com.github.claudecodegui;

import com.github.claudecodegui.settings.GlobalTabStateService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;


/**
 * Action to create a new chat tab in the Claude Code GUI tool window.
 */
public class CreateNewTabAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(CreateNewTabAction.class);

    private static final String TOOL_WINDOW_ID = "CCG";

    public CreateNewTabAction() {
        super(
            ClaudeCodeGuiBundle.message("action.createNewTab.text"),
            ClaudeCodeGuiBundle.message("action.createNewTab.description"),
            null
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            LOG.error("[CreateNewTabAction] Project is null");
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            LOG.error("[CreateNewTabAction] Tool window not found");
            return;
        }

        // Create a new chat window instance with skipRegister=true (don't replace the main instance)
        ClaudeChatWindow newChatWindow = new ClaudeChatWindow(project, true);

        // Generate the next available tab name (format: "AIN")
        String tabName = ClaudeSDKToolWindow.getNextTabName(toolWindow);

        // Create and configure the new tab content
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(newChatWindow.getContent(), tabName, false);
        content.setCloseable(true);
        newChatWindow.setParentContent(content);

        // Add the content to the tool window
        ContentManager contentManager = toolWindow.getContentManager();
        contentManager.addContent(content);
        contentManager.setSelectedContent(content);

        // Create global tab record and associate with the chat window
        GlobalTabStateService.GlobalTabRecord globalTab = GlobalTabStateService.getInstance().createTab(tabName);
        newChatWindow.setGlobalTabId(globalTab.getId());

        // Ensure the tool window is visible
        toolWindow.show(null);

        LOG.info("[CreateNewTabAction] Created new tab: " + tabName);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(e.getProject() != null);
    }
}
