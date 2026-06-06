package com.forestry.aireviewer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocxRevisionExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxRevisionExtractor.class);

    private static final String WORDML_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String DOCUMENT_XML_ENTRY = "word/document.xml";

    public boolean supports(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return true;
        }
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equalsIgnoreCase(file.getContentType());
    }

    public List<RevisionEdit> extract(MultipartFile file) {
        if (!supports(file)) {
            return List.of();
        }
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (DOCUMENT_XML_ENTRY.equals(entry.getName())) {
                    byte[] xml = zis.readAllBytes();
                    List<RevisionEdit> edits = parseDocumentXml(xml);
                    log.info("Extracted {} tracked revisions from '{}'", edits.size(), file.getOriginalFilename());
                    return edits;
                }
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to extract tracked revisions from '{}': {}",
                    file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
    }

    List<RevisionEdit> parseDocumentXml(byte[] xml) throws Exception {
        DocumentBuilder builder = secureBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml));
        NodeList paragraphs = doc.getElementsByTagNameNS(WORDML_NS, "p");

        List<RevisionEdit> edits = new ArrayList<>();
        for (int pIdx = 0; pIdx < paragraphs.getLength(); pIdx++) {
            Element paragraph = (Element) paragraphs.item(pIdx);
            collectFromParagraph(paragraph, pIdx + 1, edits);
        }
        return edits;
    }

    private void collectFromParagraph(Element paragraph, int paragraphNumber, List<RevisionEdit> out) {
        List<IndexedRevision> revs = new ArrayList<>();
        NodeList children = paragraph.getChildNodes();
        int childPosition = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!WORDML_NS.equals(child.getNamespaceURI())) {
                childPosition++;
                continue;
            }
            String localName = child.getLocalName();
            if ("ins".equals(localName)) {
                revs.add(new IndexedRevision(
                        Kind.INS,
                        attr((Element) child, "author"),
                        collectText((Element) child, "t"),
                        childPosition));
            } else if ("del".equals(localName)) {
                revs.add(new IndexedRevision(
                        Kind.DEL,
                        attr((Element) child, "author"),
                        collectText((Element) child, "delText"),
                        childPosition));
            }
            childPosition++;
        }

        String location = "Paragraph " + paragraphNumber;
        for (int i = 0; i < revs.size(); i++) {
            IndexedRevision current = revs.get(i);
            IndexedRevision next = i + 1 < revs.size() ? revs.get(i + 1) : null;
            if (next != null
                    && next.position - current.position == 1
                    && current.kind != next.kind
                    && Objects.equals(current.author, next.author)) {
                IndexedRevision del = current.kind == Kind.DEL ? current : next;
                IndexedRevision ins = current.kind == Kind.INS ? current : next;
                out.add(new RevisionEdit(current.author, del.text, ins.text, location));
                i++;
            } else if (current.kind == Kind.DEL) {
                out.add(new RevisionEdit(current.author, current.text, "", location));
            } else {
                out.add(new RevisionEdit(current.author, "", current.text, location));
            }
        }
    }

    private String collectText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(WORDML_NS, localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) {
            sb.append(nodes.item(i).getTextContent());
        }
        return sb.toString();
    }

    private String attr(Element element, String localName) {
        String value = element.getAttributeNS(WORDML_NS, localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("w:" + localName);
        }
        return value == null || value.isBlank() ? null : value;
    }

    private DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        return dbf.newDocumentBuilder();
    }

    private enum Kind { INS, DEL }

    private record IndexedRevision(Kind kind, String author, String text, int position) {}
}
