package com.adobe.epubcheck.overlay;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.adobe.epubcheck.api.EPUBLocation;
import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.opf.OPFChecker30;
import com.adobe.epubcheck.opf.ValidationContext;
import com.adobe.epubcheck.opf.XRefChecker;
import com.adobe.epubcheck.util.EpubConstants;
import com.adobe.epubcheck.util.HandlerUtil;
import com.adobe.epubcheck.util.PathUtil;
import com.adobe.epubcheck.vocab.StructureVocab;
import com.adobe.epubcheck.vocab.Vocab;
import com.adobe.epubcheck.vocab.VocabUtil;
import com.adobe.epubcheck.xml.XMLElement;
import com.adobe.epubcheck.xml.XMLHandler;
import com.adobe.epubcheck.xml.XMLParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class OverlayHandler implements XMLHandler
{

  private static Map<String, Vocab> RESERVED_VOCABS = ImmutableMap.<String, Vocab> of("",
      StructureVocab.VOCAB);
  private static Map<String, Vocab> KNOWN_VOCAB_URIS = ImmutableMap.of();
  private static Set<String> DEFAULT_VOCAB_URIS = ImmutableSet.of(StructureVocab.URI);

  private final ValidationContext context;
  private final String path;
  private final Report report;
  private final XMLParser parser;

  private boolean checkedUnsupportedXMLVersion;

  private Map<String, Vocab> vocabs = RESERVED_VOCABS;
  
  private Set<String> resourceRefs = new HashSet<String>();

  public OverlayHandler(ValidationContext context, XMLParser parser)
  {
    this.context = context;
    this.path = context.path;
    this.report = context.report;
    this.parser = parser;
    checkedUnsupportedXMLVersion = false;
  }

  public void startElement()
  {
    if (!checkedUnsupportedXMLVersion)
    {
      HandlerUtil.checkXMLVersion(parser);
      checkedUnsupportedXMLVersion = true;
    }

    XMLElement e = parser.getCurrentElement();
    String name = e.getName();

    if (name.equals("smil"))
    {
      vocabs = VocabUtil.parsePrefixDeclaration(
          e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "prefix"), RESERVED_VOCABS,
          KNOWN_VOCAB_URIS, DEFAULT_VOCAB_URIS, report,
          EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()));
    }
    else if (name.equals("seq"))
    {
      processSeq(e);
    }
    else if (name.equals("text"))
    {
      processSrc(e);
    }
    else if (name.equals("audio"))
    {
      processRef(e.getAttribute("src"), XRefChecker.Type.AUDIO);
    }
    else if (name.equals("body") || name.equals("par"))
    {
      checkType(e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "type"));
    }
  }

  private void checkType(String type)
  {
    VocabUtil.parsePropertyList(type, vocabs, context,
        EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()));
  }

  private void processSrc(XMLElement e)
  {
    processRef(e.getAttribute("src"), XRefChecker.Type.HYPERLINK);

  }

  private void processRef(String ref, XRefChecker.Type type)
  {
    if (ref != null && context.xrefChecker.isPresent())
    {
      ref = PathUtil.resolveRelativeReference(path, ref);
      if (type == XRefChecker.Type.AUDIO)
      {
        String mimeType = context.xrefChecker.get().getMimeType(ref);
        if (mimeType != null && !OPFChecker30.isBlessedAudioType(mimeType))
        {
          report.message(MessageId.MED_005, EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()), ref, mimeType);
        }
      }
      else {
        String uniqueResource = PathUtil.removeFragment(ref);
        if (!Strings.isNullOrEmpty(uniqueResource)) {
          if (!context.overlayTextChecker.get().add(uniqueResource, context.opfItem.get().getId())) {
              report.message(MessageId.MED_011, EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()), ref);
          }
        }
      }
      context.xrefChecker.get().registerReference(path, parser.getLineNumber(),
          parser.getColumnNumber(), ref, type);
    }
  }

  private void processSeq(XMLElement e)
  {
    processRef(e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "textref"),
        XRefChecker.Type.HYPERLINK);
    checkType(e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "type"));
  }

  public void characters(char[] chars, int arg1, int arg2)
  {
  }

  public void endElement()
  {
	XMLElement e = parser.getCurrentElement();
	String name = e.getName();
    if (name.equals("smil"))
    {
      checkItemReferences();
    }
  }

  public void ignorableWhitespace(char[] chars, int arg1, int arg2)
  {
  }

  public void processingInstruction(String arg0, String arg1)
  {
  }
  
  private void checkItemReferences() {

    if(this.resourceRefs.isEmpty()) {
    	return;
    }
    
  }

}
