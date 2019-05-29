package com.oxygenxml.webapp.plugins.spellcheck;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.access.EditingSessionContext;
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
public class ClearSpellingContextInformationOperation extends AuthorOperationWithResult {
  
  @Override
  public String doOperation(AuthorDocumentModel model, ArgumentsMap args) throws AuthorOperationException {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    editingContext.setAttribute(SpellcheckContext.SPELLCHECK_CONTEXT_ATTR_NAME, null);
    return null;
  }
}
