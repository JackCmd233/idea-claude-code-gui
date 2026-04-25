package com.github.claudecodegui.action.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SelectionReferenceBuilderTest {

    private final SelectionReferenceBuilder builder = new SelectionReferenceBuilder();

    @Test
    public void singleLineSelectionBuildsSingleLineReference() {
        SelectionReferenceBuilder.Result result = builder.buildFromRawSelection(
                "selected",
                "D:\\Code\\demo\\Foo.java",
                12,
                12
        );

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("@D:\\Code\\demo\\Foo.java#L12", result.getReference());
    }

    @Test
    public void multiLineSelectionBuildsRangeReference() {
        SelectionReferenceBuilder.Result result = builder.buildFromRawSelection(
                "selected",
                "D:\\Code\\demo\\Foo.java",
                12,
                24
        );

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("@D:\\Code\\demo\\Foo.java#L12-L24", result.getReference());
    }

    @Test
    public void blankSelectionFails() {
        SelectionReferenceBuilder.Result result = builder.buildFromRawSelection(
                "   ",
                "D:\\Code\\demo\\Foo.java",
                12,
                24
        );

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("send.selectCodeFirst", result.getMessageKey());
    }

    @Test
    public void blankPathFails() {
        SelectionReferenceBuilder.Result result = builder.buildFromRawSelection(
                "selected",
                "   ",
                12,
                24
        );

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("send.cannotGetFilePath", result.getMessageKey());
    }

    @Test
    public void nullEditorAndFileFailFast() {
        SelectionReferenceBuilder.Result editorResult = builder.build(null, null);
        Assert.assertFalse(editorResult.isSuccess());
        Assert.assertEquals("send.cannotGetEditor", editorResult.getMessageKey());

        SelectionReferenceBuilder.Result fileResult = builder.build(createEditor("selected", 11, 11), null);
        Assert.assertFalse(fileResult.isSuccess());
        Assert.assertEquals("send.cannotGetFile", fileResult.getMessageKey());
    }

    private static Editor createEditor(String selectedText, int startLineNumber, int endLineNumber) {
        SelectionModel selectionModel = (SelectionModel) Proxy.newProxyInstance(
                SelectionModel.class.getClassLoader(),
                new Class[]{SelectionModel.class},
                new SelectionModelHandler(selectedText, 10, 20)
        );
        Document document = (Document) Proxy.newProxyInstance(
                Document.class.getClassLoader(),
                new Class[]{Document.class},
                new DocumentHandler(startLineNumber - 1, endLineNumber - 1)
        );
        return (Editor) Proxy.newProxyInstance(
                Editor.class.getClassLoader(),
                new Class[]{Editor.class},
                new EditorHandler(selectionModel, document)
        );
    }

    private static final class EditorHandler implements InvocationHandler {
        private final SelectionModel selectionModel;
        private final Document document;

        private EditorHandler(SelectionModel selectionModel, Document document) {
            this.selectionModel = selectionModel;
            this.document = document;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getSelectionModel".equals(name)) {
                return selectionModel;
            }
            if ("getDocument".equals(name)) {
                return document;
            }
            if ("isDisposed".equals(name)) {
                return false;
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "test-editor";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class SelectionModelHandler implements InvocationHandler {
        private final String selectedText;
        private final int startOffset;
        private final int endOffset;

        private SelectionModelHandler(String selectedText, int startOffset, int endOffset) {
            this.selectedText = selectedText;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getSelectedText".equals(name)) {
                return selectedText;
            }
            if ("getSelectionStart".equals(name)) {
                return startOffset;
            }
            if ("getSelectionEnd".equals(name)) {
                return endOffset;
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "test-selection-model";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class DocumentHandler implements InvocationHandler {
        private final int startLineNumber;
        private final int endLineNumber;

        private DocumentHandler(int startLineNumber, int endLineNumber) {
            this.startLineNumber = startLineNumber;
            this.endLineNumber = endLineNumber;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getLineNumber".equals(name)) {
                int offset = (Integer) args[0];
                if (offset <= 10) {
                    return startLineNumber;
                }
                return endLineNumber;
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "test-document";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\u0000';
        }
        return null;
    }
}
