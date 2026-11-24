package dev.bbkb.ime.keyboard.inputboard.tools;

import android.view.View;

import androidx.annotation.Nullable;

import dev.bbkb.ime.R;
import dev.bbkb.ime.core.BlackBerryIME;
import dev.bbkb.ime.keyboard.inputboard.AbstractBoardController;

/** One input-menu key opens one mode of the shared in-keyboard tool board. */
public final class ToolBoardController extends AbstractBoardController<Void> {

    public static final int KEY_TEXT_EDIT = -47;
    public static final int KEY_TRANSLATE = -48;
    public static final int KEY_HANDWRITING = -49;

    private final ToolBoardView.Mode mode;

    public ToolBoardController(BlackBerryIME ime, int keyCode, ToolBoardView.Mode mode) {
        super(keyCode, ime, null);
        this.mode = mode;
    }

    @Nullable
    private ToolBoardView board() {
        if (this.ime == null || this.ime.getWindow() == null || this.ime.getWindow().getWindow() == null) {
            return null;
        }
        View found = this.ime.getWindow().getWindow().getDecorView().findViewById(R.id.inputboard_tool_view);
        return found instanceof ToolBoardView ? (ToolBoardView) found : null;
    }

    @Override
    protected View peekBoardView() {
        return board();
    }

    @Override
    public boolean isShowing() {
        ToolBoardView view = board();
        return view != null && view.isUp() && view.getMode() == this.mode;
    }

    @Override
    protected void onShow() {
        ToolBoardView view = board();
        BlackBerryIME host = this.ime;
        if (view != null && host != null) {
            host.setFlickSuggestionsSuppressed(true);
            view.bringToFront();
            view.show(this.mode, host);
        }
    }

    @Override
    protected void onHide() {
        ToolBoardView view = board();
        if (view != null && view.getMode() == this.mode) {
            view.hideBoard();
            if (this.ime != null) {
                this.ime.setFlickSuggestionsSuppressed(false);
            }
        }
    }
}
