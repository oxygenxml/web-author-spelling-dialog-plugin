package com.oxygenxml.webapp.plugins.spellcheck;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.AuthorOperationWithResult;
import ro.sync.ecss.extensions.api.webapp.WebappRestSafe;

import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckContext;

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
    SpellcheckContext spellcheckContext = new SpellcheckContext(model); 
    spellcheckContext.ignoreCurrentWord();

    return new GoToNextSpellingErrorOperation().doOperation(model, args);
  }
}
