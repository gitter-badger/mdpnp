package org.mdpnp.apps.testapp.comboboxfix;

import javafx.beans.value.ObservableValue;
import javafx.css.Styleable;
import javafx.geometry.*;
import javafx.scene.control.*;

import com.sun.javafx.scene.control.behavior.ComboBoxBaseBehavior;
import com.sun.javafx.scene.control.skin.ComboBoxBaseSkin;

import javafx.beans.InvalidationListener;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.WindowEvent;

public abstract class ComboBoxPopupControl<T> extends ComboBoxBaseSkin<T> {
    
    protected PopupControl popup;
    public static final String COMBO_BOX_STYLE_CLASS = "combo-box-popup";

    private boolean popupNeedsReconfiguring = true;

    public ComboBoxPopupControl(ComboBoxBase<T> comboBox, final ComboBoxBaseBehavior<T> behavior) {
        super(comboBox, behavior);
    }
    
    /**
     * This method should return the Node that will be displayed when the user
     * clicks on the ComboBox 'button' area.
     */
    protected abstract Node getPopupContent();
    
    protected PopupControl getPopup() {
        if (popup == null) {
            createPopup();
        }
        return popup;
    }

    @Override public void show() {
        if (getSkinnable() == null) {
            throw new IllegalStateException("ComboBox is null");
        }
        
        Node content = getPopupContent();
        if (content == null) {
            throw new IllegalStateException("Popup node is null");
        }
        
        if (getPopup().isShowing()) return;
        
        positionAndShowPopup();
    }

    @Override public void hide() {
        if (popup != null && popup.isShowing()) {
            popup.hide();
        }
    }
    
    private Point2D getPrefPopupPosition() {
        return com.sun.javafx.Utils.pointRelativeTo(getSkinnable(), getPopupContent(), HPos.CENTER, VPos.BOTTOM, 0.0, 0.0, true);
    }
    
    private void positionAndShowPopup() {
        final PopupControl _popup = getPopup();
        _popup.getScene().setNodeOrientation(getSkinnable().getEffectiveNodeOrientation());


        final Node popupContent = getPopupContent();
        sizePopup();

        Point2D p = getPrefPopupPosition();

        popupNeedsReconfiguring = true;
        reconfigurePopup();
        
        final ComboBoxBase<T> comboBoxBase = getSkinnable();
        _popup.show(comboBoxBase.getScene().getWindow(),
                snapPosition(p.getX()),
                snapPosition(p.getY()));

        popupContent.requestFocus();

        // second call to sizePopup here to enable proper sizing _after_ the popup
        // has been displayed. See RT-37622 for more detail.
        sizePopup();
    }

    private void sizePopup() {
        final Node popupContent = getPopupContent();

        if (popupContent instanceof Region) {
            // snap to pixel
            final Region r = (Region) popupContent;

            final double prefWidth = r.prefWidth(-1);
            final double minWidth = r.minWidth(-1);
            final double maxWidth = r.maxWidth(-1);
            final double w = snapSize(Math.min(Math.max(prefWidth, minWidth), Math.max(minWidth, maxWidth)));

            final double prefHeight = r.prefHeight(w);
            final double minHeight = r.minHeight(w);
            final double maxHeight = r.maxHeight(w);
            final double h = snapSize(Math.min(Math.max(prefHeight, minHeight), Math.max(minHeight, maxHeight)));

            popupContent.resize(w, h);
        } else {
            popupContent.autosize();
        }
    }
    
    private void createPopup() {
        popup = new PopupControl() {

            @Override public Styleable getStyleableParent() {
                return ComboBoxPopupControl.this.getSkinnable();
            }
            {
                setSkin(new Skin<Skinnable>() {
                    @Override public Skinnable getSkinnable() { return ComboBoxPopupControl.this.getSkinnable(); }
                    @Override public Node getNode() { return getPopupContent(); }
                    @Override public void dispose() { }
                });
            }

        };
        popup.getStyleClass().add(COMBO_BOX_STYLE_CLASS);
        popup.setConsumeAutoHidingEvents(false);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setOnAutoHide(e -> {
            getBehavior().onAutoHide();
        });
        popup.addEventHandler(MouseEvent.MOUSE_CLICKED, t -> {
            // RT-18529: We listen to mouse input that is received by the popup
            // but that is not consumed, and assume that this is due to the mouse
            // clicking outside of the node, but in areas such as the
            // dropshadow.
            getBehavior().onAutoHide();
        });
        popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, t -> {
            // Make sure the accessibility focus returns to the combo box
            // after the window closes.
            getSkinnable().notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_NODE);
        });
        
        // Fix for RT-21207
        InvalidationListener layoutPosListener = o -> {
            popupNeedsReconfiguring = true;
            reconfigurePopup();
        };
        getSkinnable().layoutXProperty().addListener(layoutPosListener);
        getSkinnable().layoutYProperty().addListener(layoutPosListener);
        getSkinnable().widthProperty().addListener(layoutPosListener);
        getSkinnable().heightProperty().addListener(layoutPosListener);

        // RT-36966 - if skinnable's scene becomes null, ensure popup is closed
        getSkinnable().sceneProperty().addListener(o -> {
            if (((ObservableValue)o).getValue() == null) {
                hide();
            }
        });

    }

    void reconfigurePopup() {
        // RT-26861. Don't call getPopup() here because it may cause the popup
        // to be created too early, which leads to memory leaks like those noted
        // in RT-32827.
        if (popup == null) return;

        final boolean isShowing = popup.isShowing();
        if (! isShowing) return;

        if (! popupNeedsReconfiguring) return;
        popupNeedsReconfiguring = false;

        final Point2D p = getPrefPopupPosition();

        final Node popupContent = getPopupContent();
        final double minWidth = popupContent.prefWidth(Region.USE_COMPUTED_SIZE);
        final double minHeight = popupContent.prefHeight(Region.USE_COMPUTED_SIZE);

        if (p.getX() > -1) popup.setAnchorX(p.getX());
        if (p.getY() > -1) popup.setAnchorY(p.getY());
        if (minWidth > -1) popup.setMinWidth(minWidth);
        if (minHeight > -1) popup.setMinHeight(minHeight);

        final Bounds b = popupContent.getLayoutBounds();
        final double currentWidth = b.getWidth();
        final double currentHeight = b.getHeight();
        final double newWidth  = currentWidth < minWidth ? minWidth : currentWidth;
        final double newHeight = currentHeight < minHeight ? minHeight : currentHeight;

        if (newWidth != currentWidth || newHeight != currentHeight) {
            // Resizing content to resolve issues such as RT-32582 and RT-33700
            // (where RT-33700 was introduced due to a previous fix for RT-32582)
            popupContent.resize(newWidth, newHeight);
            if (popupContent instanceof Region) {
                ((Region)popupContent).setMinSize(newWidth, newHeight);
                ((Region)popupContent).setPrefSize(newWidth, newHeight);
            }
        }
    }
}
