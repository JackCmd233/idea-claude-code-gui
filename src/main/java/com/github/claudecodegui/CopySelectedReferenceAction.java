package com.github.claudecodegui;

import com.github.claudecodegui.notifications.ClaudeNotifier;
import com.github.claudecodegui.util.SelectionReferenceUtils;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * Copies the current editor selection reference to the system clipboard.
 */
public class CopySelectedReferenceAction extends AnAction implements DumbAware {

    private static final Logger LOG = Logger.getInstance(CopySelectedReferenceAction.class);

    public CopySelectedReferenceAction() {
        super(
            ClaudeCodeGuiBundle.message("action.copySelectedReference.text"),
            ClaudeCodeGuiBundle.message("action.copySelectedReference.description"),
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
            return;
        }

        try {
            ReadAction
                .nonBlocking(() -> SelectionReferenceUtils.getSelectionReferenceFromEvent(e))
                .finishOnUiThread(ModalityState.defaultModalityState(), reference -> {
                    if (reference == null) {
                        ClaudeNotifier.showWarning(project, ClaudeCodeGuiBundle.message("send.selectCodeFirst"));
                        return;
                    }
                    copyToClipboard(project, reference);
                })
                .submit(AppExecutorUtil.getAppExecutorService());
        } catch (Exception ex) {
            ClaudeNotifier.showError(project, ClaudeCodeGuiBundle.message("copyReference.failed", ex.getMessage()));
            LOG.error("Failed to copy selected reference", ex);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        e.getPresentation().setEnabledAndVisible(selectedText != null && !selectedText.isEmpty());
    }

    private void copyToClipboard(@NotNull Project project, @NotNull String reference) {
        try {
            CopyPasteManager.getInstance().setContents(new StringSelection(reference));
            ClaudeNotifier.showSuccess(project, ClaudeCodeGuiBundle.message("copyReference.success"));
            LOG.info("Copied selected reference: " + reference);
        } catch (Exception ex) {
            ClaudeNotifier.showError(project, ClaudeCodeGuiBundle.message("copyReference.failed", ex.getMessage()));
            LOG.error("Failed to copy reference to clipboard", ex);
        }
    }
}
