package com.github.claudecodegui.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for building editor selection references in @path#Lx-y format.
 *
 * <p>NOTE: Methods accepting {@link Editor} must be called within a Read Action
 * when accessing document content or file information.
 */
public final class SelectionReferenceUtils {

    private SelectionReferenceUtils() {
    }

    /**
     * Builds a reference for the current editor selection.
     *
     * @param editor active editor
     * @return formatted reference, or null when selection/file info is unavailable
     */
    public static @Nullable String buildReference(@NotNull Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            return null;
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null) {
            return null;
        }

        return buildReference(
            virtualFile.getPath(),
            editor.getDocument(),
            selectionModel.getSelectionStart(),
            selectionModel.getSelectionEnd()
        );
    }

    /**
     * Builds a reference from document offsets.
     *
     * @param filePath absolute file path
     * @param document editor document
     * @param startOffset selection start offset
     * @param endOffset selection end offset
     * @return formatted reference, or null when inputs are invalid
     */
    public static @Nullable String buildReference(@Nullable String filePath,
                                                  @NotNull Document document,
                                                  int startOffset,
                                                  int endOffset) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        if (startOffset < 0 || endOffset < startOffset || endOffset > document.getTextLength()) {
            return null;
        }

        int startLine = document.getLineNumber(startOffset) + 1;
        int endLine = document.getLineNumber(endOffset) + 1;
        return formatReference(filePath, startLine, endLine);
    }

    /**
     * Formats a file path and line range to the reference syntax understood by CCG.
     */
    static @Nullable String formatReference(@Nullable String filePath, int startLine, int endLine) {
        if (filePath == null || filePath.trim().isEmpty() || startLine <= 0 || endLine < startLine) {
            return null;
        }
        String linePart = startLine == endLine
                ? String.valueOf(startLine)
                : startLine + "-" + endLine;
        return "@" + filePath + "#L" + linePart;
    }

    /**
     * Extracts the selection reference from an action event.
     *
     * <p>This method checks for editor selection and builds a reference string.
     * Must be called within a Read Action when accessing editor content.
     *
     * @param e the action event containing editor and project context
     * @return formatted reference, or null if selection is unavailable
     */
    public static @Nullable String getSelectionReferenceFromEvent(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return null;
        }

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return null;
        }

        return buildReference(editor);
    }
}
