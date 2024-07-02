package dev.lukebemish.immaculate.eclipsejdtwrapper;

import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        String fileName = args[0];
        Map<String, String> options;
        if (args.length >= 2) {
            File configFile = new File(args[1]);
            options = readProperties(configFile);
        } else {
            options = new HashMap<>();
        }
        var formatter = new DefaultCodeFormatter(options);
        int kind = CodeFormatter.F_INCLUDE_COMMENTS;
        if (fileName.equals("module-info.java")) {
            kind |= CodeFormatter.K_MODULE_INFO;
        } else {
            kind |= CodeFormatter.K_COMPILATION_UNIT;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            String source = builder.toString();
            var textEdit = formatter.format(kind, source, 0, source.length(), 0, "\n");
            if (textEdit == null) {
                throw new RuntimeException("Failed to format source.");
            }
            var document = new Document(source);
            textEdit.apply(document);
            System.out.print(document.get());
        } catch (IOException | BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> readProperties(File configFile) {
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            var db = dbf.newDocumentBuilder();
            var rootNode = db.parse(configFile).getDocumentElement();
            Map<String, String> properties = new HashMap<>();

            // Is this the only format? I'm not actually sure...
            NodeList children = rootNode.getChildNodes();
            List<Node> profiles = new ArrayList<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if ("profile".equals(child.getNodeName())) {
                    profiles.add(child);
                }
            }
            if (profiles.isEmpty()) {
                throw new IllegalArgumentException("No 'profile' nodes found in the configuration file.");
            }
            if (profiles.size() > 1) {
                throw new IllegalArgumentException("Multiple 'profile' nodes found in the configuration file.");
            }
            Node firstProfile = profiles.get(0);
            NodeList profileChildren = firstProfile.getChildNodes();
            List<Node> settingsNodes = new ArrayList<>();
            for (int i = 0; i < profileChildren.getLength(); i++) {
                Node child = profileChildren.item(i);
                if ("setting".equals(child.getNodeName())) {
                    settingsNodes.add(child);
                }
            }
            for (Node settings : settingsNodes) {
                NamedNodeMap attributes = settings.getAttributes();
                Node id = attributes.getNamedItem("id");
                Node value = attributes.getNamedItem("value");
                if (id == null) {
                    throw new RuntimeException("'setting' node missing 'id' attribute.");
                }
                properties.put(id.getNodeValue(), (value == null) ? "" : value.getNodeValue());
            }
            return properties;
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }
}