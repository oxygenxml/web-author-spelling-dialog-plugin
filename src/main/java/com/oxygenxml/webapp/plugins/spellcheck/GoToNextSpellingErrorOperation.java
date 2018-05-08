package com.oxygenxml.webapp.plugins.spellcheck;
        
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.swing.text.BadLocationException;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import com.google.common.collect.ImmutableMap;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;
import ro.sync.ecss.extensions.api.SpellSuggestionsInfo;
import ro.sync.ecss.extensions.api.access.EditingSessionContext;
import ro.sync.ecss.extensions.api.node.AuthorDocument;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.AuthorOperationWithResult;
import ro.sync.ecss.extensions.api.webapp.WebappRestSafe;
import ro.sync.ecss.extensions.api.webapp.WebappSpellchecker;
import ro.sync.exml.workspace.api.util.TextChunkDescriptor;

/**
 * Operation that goes to the next spelling error.
 * 
 * @author ctalau
 */
@WebappRestSafe
public class GoToNextSpellingErrorOperation extends AuthorOperationWithResult {
  /**
   * Logger.
   */
  Logger logger = Logger.getLogger(GoToNextSpellingErrorOperation.class);
  
  /**
   * Attribute name for the start position of the last spelling error.
   */
  private static final String START_POS_ATTR_NAME = "com.oxygenxml.plugins.spellcheck.startPos";
  
  /**
   * Attribute name for the end position of the last spelling error.
   */
  private static final String END_POS_ATTR_NAME = "com.oxygenxml.plugins.spellcheck.endPos";

  @Override
  public String doOperation(AuthorDocumentModel docModel, ArgumentsMap args)
      throws IllegalArgumentException, AuthorOperationException {
    boolean fromCaret = (Boolean)args.getArgumentValue("fromCaret");
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    
    SpellCheckingProblemInfo nextProblem = findNextProblem(docModel, fromCaret);
    if (nextProblem == null) {
      return null;
    }
    
    saveCurrentProblem(docModel, nextProblem);
    
    // Select the next spelling error.
    docModel.getSelectionModel().setSelection(
        nextProblem.getStartOffset(), nextProblem.getEndOffset() + 1);
    
    String[] suggestions = findSuggestions(spellchecker, nextProblem);
    
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      return objectMapper.writeValueAsString(ImmutableMap.of(
          "word", nextProblem.getWord(),
          "suggestions", suggestions));
    } catch (IOException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
  }

  /**
   * Return the offset from which we should start the spellchecking.
   * 
   * @param docModel the document model.
   * @param fromCaret <code>true</code> if we should start from the carer pos.
   * 
   * @return The start offset.
   */
  private int getStartOffset(AuthorDocumentModel docModel, boolean fromCaret) {
    if (fromCaret) {
      return docModel.getSelectionModel().getCaretOffset();
    } else {
      EditingSessionContext editingContext = docModel.getAuthorAccess().getEditorAccess().getEditingContext();
      Integer lastEnd = (Integer) editingContext.getAttribute(END_POS_ATTR_NAME);
      return lastEnd != null ? lastEnd : 0;
    }
  }

  /**
   * Save the position of the current spelling error.
   * 
   * @param docModel The document model.
   * @param fromCaret <code>true</code> to return the next problem from the caret position
   * rather than the last position marked.
   * 
   * @return
   * @throws AuthorOperationException
   */
  private SpellCheckingProblemInfo findNextProblem(AuthorDocumentModel docModel, boolean fromCaret)
      throws AuthorOperationException {
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    AuthorDocument document = docModel.getAuthorDocumentController().getAuthorDocumentNode();
    
    int startOffset = getStartOffset(docModel, fromCaret);
    
    SpellCheckingProblemInfo problemInfo = 
        runSpellcheck(spellchecker, startOffset, document.getEndOffset(), document.getEndOffset());
    if (problemInfo == null) {
      problemInfo = runSpellcheck(spellchecker, 0, startOffset, document.getEndOffset());
    }
    return problemInfo;
  }

  /**
   * Run spellcheck between two offsets.
   * 
   * @param spellchecker The spellchecker.
   * @param startOffset The start offset.
   * @param endOffset The end offset.
   * @param docLength The length of the document.
   * 
   * @return The first problem found, or null if everything is ok.
   * 
   * @throws AuthorOperationException
   */
  private SpellCheckingProblemInfo runSpellcheck(WebappSpellchecker spellchecker,
      int startOffset, int endOffset, int docLength) throws AuthorOperationException {
    logger.debug("Checking between " + startOffset + " " + endOffset);
    int interval = 1000;
    
    int currentOffset = startOffset;
    while (currentOffset < endOffset) {
      int intervalEnd = Math.min(currentOffset + interval, endOffset);
      SpellCheckingProblemInfo nextProblem = 
          runSpellcheckSingleInterval(spellchecker, currentOffset, intervalEnd, docLength);
      if (nextProblem != null) {
        logger.debug("Found: " + nextProblem.getWord());
        return nextProblem;
      }
      currentOffset = Math.min(currentOffset + interval, endOffset);
    }
    
    return null;
  }

  /**
   * Runs spellcheck on a single interval.
   * 
   * @param spellchecker The spellchecker.
   * @param start The start offset of the interval.
   * @param end The end offset of the interval.
   * @param docLength The length of the document.
   * 
   * @return The first problem found or null if no problems were found.
   * 
   * @throws AuthorOperationException
   */
  private SpellCheckingProblemInfo runSpellcheckSingleInterval(WebappSpellchecker spellchecker,
      int start, int end, int docLength) throws AuthorOperationException {
    logger.debug("Checking interval between " + start + " " + end);

    // Run spellcheck on a slightly larger interval and ignore problems
    // at the boundaries - they may be caused by truncated words.
    int boundary = 50;
    List<TextChunkDescriptor> textDescriptors = 
        spellchecker.getTextDescriptors(
            Math.max(0, start - boundary), 
            Math.min(docLength, end + boundary));
    
    for (TextChunkDescriptor textDescriptor : textDescriptors) {
      try {
        List<SpellCheckingProblemInfo> problems = 
            spellchecker.check(Collections.singletonList(textDescriptor));
        if (problems == null) {
          continue;
        }
        for (SpellCheckingProblemInfo problem : problems) {
          if (problem.getStartOffset() >= start && problem.getEndOffset() <= end) {
            return problem;
          } else {
            // We might just found a truncated word at the end or start of the interval. 
            // It will be found again in the next overlapping interval.  
          }
        }
      } catch (IOException e) {
        throw new AuthorOperationException(e.getMessage(), e);
      }
    }
    return null;
  }

  /**
   * Saves the current spelling problem so that we can resume from it the next time.
   * 
   * @param model The document model.
   * @param currentProblem The current problem.
   * 
   * @throws AuthorOperationException
   */
  private void saveCurrentProblem(AuthorDocumentModel model, SpellCheckingProblemInfo currentProblem)
      throws AuthorOperationException {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    try {
      AuthorDocumentController controller = model.getAuthorDocumentController();
      editingContext.setAttribute(START_POS_ATTR_NAME, 
          controller.createPositionInContent(currentProblem.getStartOffset()));
      editingContext.setAttribute(END_POS_ATTR_NAME, 
          controller.createPositionInContent(currentProblem.getEndOffset()));
    } catch (BadLocationException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
  }

  /**
   * Finds suggestions for the current spelling problem.
   * 
   * @param spellchecker The spellchecker.
   * @param nextProblem The spelling problem.
   * 
   * @return The list of suggestions.
   * 
   * @throws AuthorOperationException
   */
  private String[] findSuggestions(WebappSpellchecker spellchecker, SpellCheckingProblemInfo nextProblem)
      throws AuthorOperationException {
    String[] suggestions = new String[0];
    try {
      SpellSuggestionsInfo suggestionInfo = 
          spellchecker.getSuggestionsForWordAtPosition(nextProblem.getStartOffset() + 1);
      suggestions = suggestionInfo.getSuggestions();
    } catch (Exception e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
    return suggestions;
  }
}
