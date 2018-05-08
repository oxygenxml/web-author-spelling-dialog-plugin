package com.oxygenxml.webapp.plugins.spellcheck;
        
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.swing.text.BadLocationException;

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
    
    saveCurrentProblem(docModel, nextProblem);
    
    // Select the next spelling error.
    docModel.getSelectionModel().setSelection(
        nextProblem.getStartOffset(), nextProblem.getEndOffset() + 1);
    
    String[] suggestions = findSuggestions(spellchecker, nextProblem);
    
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.writeValueAsString(ImmutableMap.of(
          "word", nextProblem.getWord(),
          "suggestions", suggestions));
    } catch (IOException e) {
      throw new AuthorOperationException(e.getMessage(), e);
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
    int caretOffset = docModel.getSelectionModel().getCaretOffset();
    AuthorDocument document = docModel.getAuthorDocumentController().getAuthorDocumentNode();
    List<TextChunkDescriptor> textDescriptors;
    
    //TODO: start from the caret position or from last position.
    if (fromCaret) {
      textDescriptors = spellchecker.getTextDescriptors(caretOffset, document.getEndOffset());
    } else {
      textDescriptors = spellchecker.getTextDescriptors(document.getStartOffset(), document.getEndOffset());
    }
    
    SpellCheckingProblemInfo nextProblem = null;
    for (TextChunkDescriptor textDescriptor : textDescriptors) {
      try {
        List<SpellCheckingProblemInfo> problems = 
            spellchecker.check(Collections.singletonList(textDescriptor));
        
        if (problems != null && !problems.isEmpty()) {
          nextProblem = problems.get(0);
          break;
        }
      } catch (IOException e) {
        throw new AuthorOperationException(e.getMessage(), e);
      }
    }
    return nextProblem;
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
          spellchecker.getSuggestionsForWordAtPosition(nextProblem.getStartOffset());
      suggestions = suggestionInfo.getSuggestions();
    } catch (Exception e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
    return suggestions;
  }
}
