package com.oxygenxml.webapp.plugins.spellcheck;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.access.EditingSessionContext;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.AuthorOperationWithResult;
import ro.sync.ecss.extensions.api.webapp.WebappRestSafe;

import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckContext;
import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckWordInfo;

/**
 * Ignore current spelling error and find next problem.
 * 
 * @author mihaela
 */
@WebappRestSafe
public class IgnoreCurrentAndFindNextSpellingOperation extends AuthorOperationWithResult {
  
  /**
   * Attribute name for ignored words. Necessary for finding the next spellcheck problem
   */
  public static final String IGNORED_WORDS_ARGUMENT_NAME = "ignoredWords";
  
  @Override
  public String doOperation(AuthorDocumentModel model, ArgumentsMap args) throws AuthorOperationException {
    SpellcheckWordInfo currentWord = ignoreCurrentWord(model);
    model.getSelectionModel().moveTo(currentWord.getStartPosition().getOffset() + currentWord.getWord().length());

    return new GoToNextSpellingErrorOperation().doOperation(model, args);
  }
  
  /**
   * Add current word to ignored words.
   * 
   * @param model Author document model.
   * @param wordInfo The word info.
   * @return The ignored word.
   */
  public SpellcheckWordInfo ignoreCurrentWord(AuthorDocumentModel model) {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    SpellcheckContext spellcheckContext = (SpellcheckContext) editingContext.getAttribute(SpellcheckContext.SPELLCHECK_CONTEXT_ATTR_NAME);
    
    spellcheckContext.ignoreCurrentWord();
    return spellcheckContext.getCurrentWord();
  }
}
