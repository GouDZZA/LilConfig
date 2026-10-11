package by.likebebras.lilconfig.internal.yaml;

import by.likebebras.lilconfig.ConfigException;
import by.likebebras.lilconfig.annotation.formatting.DocumentMarkers;
import by.likebebras.lilconfig.annotation.formatting.Footer;
import by.likebebras.lilconfig.annotation.formatting.Header;
import by.likebebras.lilconfig.annotation.formatting.InlineComment;
import by.likebebras.lilconfig.annotation.formatting.KeysOrder;
import by.likebebras.lilconfig.annotation.formatting.KeysSpacing;
import by.likebebras.lilconfig.annotation.formatting.LineBreak;
import by.likebebras.lilconfig.annotation.formatting.LineWidth;
import by.likebebras.lilconfig.annotation.formatting.ListStyle;
import by.likebebras.lilconfig.annotation.formatting.MapStyle;
import by.likebebras.lilconfig.annotation.formatting.Order;
import by.likebebras.lilconfig.annotation.formatting.SpaceAfter;
import by.likebebras.lilconfig.annotation.formatting.SpaceBefore;
import by.likebebras.lilconfig.annotation.formatting.StringStyle;
import by.likebebras.lilconfig.annotation.formatting.YamlIndent;
import by.likebebras.lilconfig.format.CollectionStyle;
import by.likebebras.lilconfig.format.KeyOrder;
import by.likebebras.lilconfig.format.Newline;
import by.likebebras.lilconfig.format.StringFormat;
import by.likebebras.lilconfig.internal.tree.EncodedConfig;
import by.likebebras.lilconfig.internal.tree.YamlPaths;

import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.representer.Representer;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.*;

/** Decorates YAML nodes, never text lines: quoted keys and multiline values stay intact. */
public final class YamlFormatter {
    private static final Set<Class<? extends Annotation>> ANNOTATIONS = Set.of(
            KeysSpacing.class, KeysOrder.class, Order.class, StringStyle.class, ListStyle.class,
            MapStyle.class, SpaceBefore.class, SpaceAfter.class, InlineComment.class,
            Header.class, Footer.class, YamlIndent.class, LineWidth.class, LineBreak.class, DocumentMarkers.class);

    private final EncodedConfig encoded;

    private YamlFormatter(EncodedConfig encoded) { this.encoded = encoded; }

    public static void validate(EncodedConfig encoded, Class<?> model) {
        options(model);
        encoded.formatting().forEach((path, elements) -> {
            for (AnnotatedElement element : elements) {
                Settings.defaults().with(element);
                SpaceBefore before = element.getAnnotation(SpaceBefore.class);
                if (before != null) spacing(before.value(), "@SpaceBefore");
                SpaceAfter after = element.getAnnotation(SpaceAfter.class);
                if (after != null) spacing(after.value(), "@SpaceAfter");
                InlineComment comment = element.getAnnotation(InlineComment.class);
                if (comment != null && (comment.value().contains("\n") || comment.value().contains("\r"))) {
                    throw new ConfigException("@InlineComment must be one line at " + path);
                }
            }
        });
    }

    static String render(YamlConfiguration configuration, EncodedConfig encoded, Class<?> model) {
        boolean enabled = encoded.formatting().values().stream().flatMap(Collection::stream)
                .anyMatch(element -> ANNOTATIONS.stream().anyMatch(element::isAnnotationPresent));
        if (!enabled) return configuration.saveToString();

        // Keep document comments outside mapping entries so sorting cannot move them.
        List<String> header = configuration.options().getHeader();
        List<String> footer = configuration.options().getFooter();
        configuration.options().setHeader(List.of()).setFooter(List.of());
        String source;
        try { source = configuration.saveToString(); }
        finally { configuration.options().setHeader(header).setFooter(footer); }

        DumperOptions options = options(model);
        LoaderOptions loader = new LoaderOptions();
        loader.setProcessComments(true);
        loader.setNestingDepthLimit(256);
        Yaml yaml = new Yaml(new SafeConstructor(loader), new Representer(options), options, loader);
        Node root = yaml.compose(new StringReader(source));
        if (root == null) root = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
        root = new YamlFormatter(encoded).decorate(root, List.of(), Settings.defaults(), false);
        if (!header.isEmpty()) {
            List<CommentLine> lines = commentLines(header);
            lines.add(blank());
            if (root.getBlockComments() != null) lines.addAll(root.getBlockComments());
            root.setBlockComments(lines);
        }
        if (footer.stream().anyMatch(Objects::nonNull)) {
            List<CommentLine> lines = new ArrayList<>();
            if (root.getEndComments() != null) lines.addAll(root.getEndComments());
            lines.add(blank());
            lines.addAll(commentLines(footer));
            root.setEndComments(lines);
        }
        StringWriter writer = new StringWriter();
        yaml.serialize(root, writer);
        return writer.toString();
    }

    private static DumperOptions options(Class<?> model) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setProcessComments(true);
        options.setNonPrintableStyle(DumperOptions.NonPrintableStyle.ESCAPE);
        YamlIndent indent = model.getAnnotation(YamlIndent.class);
        if (indent != null) {
            if (indent.value() < 2 || indent.value() > 9) throw new ConfigException("@YamlIndent must be between 2 and 9");
            options.setIndent(indent.value());
        }
        LineWidth width = model.getAnnotation(LineWidth.class);
        if (width != null) {
            if (width.value() != 0 && width.value() < 20) throw new ConfigException("@LineWidth must be 0 or at least 20");
            options.setSplitLines(width.value() != 0);
            if (width.value() != 0) options.setWidth(width.value());
        }
        LineBreak lineBreak = model.getAnnotation(LineBreak.class);
        if (lineBreak != null) options.setLineBreak(lineBreak.value() == Newline.CRLF
                ? DumperOptions.LineBreak.WIN : DumperOptions.LineBreak.UNIX);
        DocumentMarkers markers = model.getAnnotation(DocumentMarkers.class);
        if (markers != null) {
            options.setExplicitStart(markers.start());
            options.setExplicitEnd(markers.end());
        }
        return options;
    }

    private Node decorate(Node node, List<String> path, Settings inherited, boolean flowParent) {
        Settings settings = inherited;
        for (AnnotatedElement element : encoded.formatting().getOrDefault(path, List.of())) settings = settings.with(element);
        if (node instanceof ScalarNode scalar && Tag.STR.equals(node.getTag()) && settings.strings != null) {
            DumperOptions.ScalarStyle style = switch (settings.strings) {
                case AUTO -> scalar.getValue().contains("\n") ? DumperOptions.ScalarStyle.LITERAL : DumperOptions.ScalarStyle.PLAIN;
                case SINGLE_QUOTED -> DumperOptions.ScalarStyle.SINGLE_QUOTED;
                case DOUBLE_QUOTED -> DumperOptions.ScalarStyle.DOUBLE_QUOTED;
                case LITERAL -> DumperOptions.ScalarStyle.LITERAL;
                case FOLDED -> DumperOptions.ScalarStyle.FOLDED;
            };
            ScalarNode replacement = new ScalarNode(scalar.getTag(), scalar.getValue(), scalar.getStartMark(), scalar.getEndMark(), style);
            replacement.setBlockComments(scalar.getBlockComments());
            replacement.setInLineComments(scalar.getInLineComments());
            replacement.setEndComments(scalar.getEndComments());
            return replacement;
        }
        if (node instanceof SequenceNode sequence) {
            if (settings.lists != null) sequence.setFlowStyle(flow(settings.lists));
            boolean flow = flowParent || sequence.getFlowStyle() == DumperOptions.FlowStyle.FLOW;
            for (int i = 0; i < sequence.getValue().size(); i++) {
                sequence.getValue().set(i, decorate(sequence.getValue().get(i), YamlPaths.childPath(path, Integer.toString(i)), settings, flow));
            }
        } else if (node instanceof MappingNode mapping) {
            if (settings.maps != null) mapping.setFlowStyle(flow(settings.maps));
            boolean flow = flowParent || mapping.getFlowStyle() == DumperOptions.FlowStyle.FLOW;
            List<NodeTuple> entries = mapping.getValue();
            List<String> declared = encoded.propertyOrder().getOrDefault(path, List.of());
            Comparator<NodeTuple> order = Comparator.comparingInt(tuple -> priority(YamlPaths.childPath(path, key(tuple))));
            order = switch (settings.order) {
                case PRESERVE -> order;
                case DECLARATION -> order.thenComparingInt(tuple -> {
                    int index = declared.indexOf(key(tuple));
                    return index < 0 ? Integer.MAX_VALUE : index;
                });
                case ALPHABETICAL -> order.thenComparing(YamlFormatter::key);
                case REVERSE_ALPHABETICAL -> order.thenComparing(YamlFormatter::key, Comparator.reverseOrder());
            };
            entries.sort(order);
            int afterPrevious = 0;
            for (int i = 0; i < entries.size(); i++) {
                NodeTuple tuple = entries.get(i);
                List<String> child = YamlPaths.childPath(path, key(tuple));
                Node value = decorate(tuple.getValueNode(), child, settings, flow);
                InlineComment inline = annotation(child, InlineComment.class);
                if (inline != null) {
                    List<CommentLine> comments = inline.value().isEmpty() ? List.of()
                            : List.of(new CommentLine(null, null, " " + inline.value(), CommentType.IN_LINE));
                    // Block collection inline comments belong after the key's colon.
                    Node target = value instanceof CollectionNode<?> collection
                            && collection.getFlowStyle() != DumperOptions.FlowStyle.FLOW && !flow ? tuple.getKeyNode() : value;
                    tuple.getKeyNode().setInLineComments(List.of());
                    value.setInLineComments(List.of());
                    target.setInLineComments(comments);
                }
                if (!flow) {
                    SpaceBefore before = annotation(child, SpaceBefore.class);
                    int gap = Math.max(afterPrevious, before == null ? 0 : before.value());
                    if (i > 0 && settings.spacing != null) gap = Math.max(gap, settings.spacing);
                    if (gap > 0 || settings.spacing != null || before != null || afterPrevious > 0) setGap(tuple.getKeyNode(), gap);
                }
                SpaceAfter after = annotation(child, SpaceAfter.class);
                afterPrevious = after == null ? 0 : after.value();
                entries.set(i, new NodeTuple(tuple.getKeyNode(), value));
            }
            if (!flow && afterPrevious > 0) {
                List<CommentLine> end = new ArrayList<>();
                for (int i = 0; i < afterPrevious; i++) end.add(blank());
                if (mapping.getEndComments() != null) end.addAll(mapping.getEndComments());
                mapping.setEndComments(end);
            }
        }
        return node;
    }

    private int priority(List<String> path) {
        Order annotation = annotation(path, Order.class);
        return annotation == null ? 0 : annotation.value();
    }

    private <T extends Annotation> T annotation(List<String> path, Class<T> type) {
        T result = null;
        for (AnnotatedElement element : encoded.formatting().getOrDefault(path, List.of())) {
            T annotation = element.getAnnotation(type);
            if (annotation != null) result = annotation;
        }
        return result;
    }

    private static String key(NodeTuple tuple) { return ((ScalarNode) tuple.getKeyNode()).getValue(); }
    private static DumperOptions.FlowStyle flow(CollectionStyle style) {
        return style == CollectionStyle.FLOW ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK;
    }

    private static void setGap(Node key, int count) {
        List<CommentLine> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) lines.add(blank());
        if (key.getBlockComments() != null) {
            // Existing separators are replaced, rather than accumulating on every save.
            List<CommentLine> old = key.getBlockComments();
            int start = 0;
            while (start < old.size() && old.get(start).getCommentType() == CommentType.BLANK_LINE) start++;
            lines.addAll(old.subList(start, old.size()));
        }
        key.setBlockComments(lines);
    }

    private static CommentLine blank() { return new CommentLine(null, null, "", CommentType.BLANK_LINE); }
    private static List<CommentLine> commentLines(List<String> text) {
        List<CommentLine> lines = new ArrayList<>();
        for (String line : text) {
            if (line == null) lines.add(blank());
            else for (String part : line.split("\\R", -1)) lines.add(new CommentLine(null, null, " " + part, CommentType.BLOCK));
        }
        return lines;
    }

    private static void spacing(int value, String annotation) {
        if (value < 0 || value > 10) throw new ConfigException(annotation + " must be between 0 and 10");
    }

    private record Settings(Integer spacing, KeyOrder order, StringFormat strings, CollectionStyle lists, CollectionStyle maps) {
        static Settings defaults() { return new Settings(null, KeyOrder.PRESERVE, null, null, null); }
        Settings with(AnnotatedElement element) {
            KeysSpacing gap = element.getAnnotation(KeysSpacing.class);
            if (gap != null) YamlFormatter.spacing(gap.value(), "@KeysSpacing");
            KeysOrder ordering = element.getAnnotation(KeysOrder.class);
            StringStyle string = element.getAnnotation(StringStyle.class);
            ListStyle list = element.getAnnotation(ListStyle.class);
            MapStyle map = element.getAnnotation(MapStyle.class);
            return new Settings(gap == null ? spacing : Integer.valueOf(gap.value()), ordering == null ? order : ordering.value(),
                    string == null ? strings : string.value(), list == null ? lists : list.value(), map == null ? maps : map.value());
        }
    }
}
