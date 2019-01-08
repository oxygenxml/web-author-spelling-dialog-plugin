package com.oxygenxml.webapp.plugins.spellcheck;
        
import java.io.IOException;
import java.util.Optional;

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
      throws AuthorOperationException {
    String result = null;
    boolean fromCaret = (Boolean)args.getArgumentValue("fromCaret");
    IgnoredWords ignoredWords = 
        IgnoredWords.fromUncheckedArgument(args.getArgumentValue("ignoredWords"));
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    
    Optional<SpellCheckingProblemInfo> maybeNextProblem = 
        findNextProblem(docModel, fromCaret, ignoredWords);
    
    if (maybeNextProblem.isPresent()) {
      SpellCheckingProblemInfo nextProblem = maybeNextProblem.get();
      
      saveCurrentProblem(docModel, nextProblem);
      
      // Select the next spelling error.
      docModel.getSelectionModel().setSelection(
          nextProblem.getStartOffset(), nextProblem.getEndOffset() + 1);
      
      // Custom spell checker may provide suggestions with the problem info.
      String[] suggestions;
      if (nextProblem.getSuggestions() != null) {
        suggestions = nextProblem.getSuggestions().toArray(new String[0]);
      } else {
        suggestions = findSuggestions(spellchecker, nextProblem);
      }
      
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        result = objectMapper.writeValueAsString(ImmutableMap.of(
            "word", nextProblem.getWord(),
            "language", nextProblem.getLanguageIsoName(),
            "startOffset", nextProblem.getStartOffset(),
            "endOffset", nextProblem.getEndOffset(),
            "suggestions", suggestions));
      } catch (IOException e) {
        throw new AuthorOperationException(e.getMessage(), e);
      }
    }
    
    return result;
  }

  /**
   * Save the position of the current spelling error.
   * 
   * @param docModel The document model.
   * @param fromCaret <code>true</code> to return the next problem from the caret position
   * rather than the last position marked.
   * @param ignoredWords The ignored words.
   * 
   * @return Info about the next spell-checking problem, if any.
   * @throws AuthorOperationException
   */
  private Optional<SpellCheckingProblemInfo> findNextProblem(AuthorDocumentModel docModel, boolean fromCaret, IgnoredWords ignoredWords)
      throws AuthorOperationException {
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    AuthorDocument document = docModel.getAuthorDocumentController().getAuthorDocumentNode();
    
    int startOffset = getStartOffset(docModel, fromCaret);
    
    SpellcheckPerformer spellcheckPerformer = new SpellcheckPerformer(
        spellchecker, 
        ignoredWords,
        document.getEndOffset());
    
    Optional<SpellCheckingProblemInfo> problemInfo = 
        spellcheckPerformer.runSpellcheck(startOffset, document.getEndOffset());
    if (!problemInfo.isPresent()) {
      problemInfo = spellcheckPerformer.runSpellcheck(0, startOffset);
    }
    return problemInfo;
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
