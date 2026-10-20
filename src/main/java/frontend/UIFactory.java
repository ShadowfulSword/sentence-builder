// Oudom Pach OTP220001 10/20/2026
package frontend;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public final class UIFactory {
    private UIFactory() {}

    public static Label label(String text, String style) {
        Label l = new Label(text);
        if (!style.isEmpty()) l.getStyleClass().add(style);
        return l;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        if (!style.isEmpty()) b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        return b;
    }

    public static VBox panel(String title, Node... children) {
        VBox box = new VBox(12);
        box.getStyleClass().add("panel");
        box.getChildren().add(label(title, "panel-title"));
        box.getChildren().addAll(children);
        return box;
    }

    public static VBox page(String title, String subtitle, Node content) {
        VBox page = new VBox(12,
                label("YOUR LANGUAGE LAB", "eyebrow"),
                label(title, "title"),
                label(subtitle, "muted"),
                content
        );
        page.setPadding(new Insets(25));
        VBox.setVgrow(content, Priority.ALWAYS);
        return page;
    }

    public static <T> TableColumn<T, String> column(TableView<T> table, String title, Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        table.getColumns().add(c);
        return c;
    }

    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss"));
    }
}