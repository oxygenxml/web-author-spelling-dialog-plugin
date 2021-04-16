describe('ManualSpellcheckingTest', function() {
  it('action should call callback immediately', function (done) {
    let editor = stubEditor();
    let manSpAction = new SpellcheckAction(editor);
    let cb = sinon.spy();
    manSpAction.actionPerformed(cb);
    assert(cb.callCount === 1);
    done();
  });

  it('ignore should resolve the promise', function (done) {
    let editor = stubEditor();
    editor.getEditingSupport().getOperationsInvoker()
        .invoke.returns(Promise.resolve());

    let manSpAction = new SpellcheckAction(editor);
    manSpAction.ignore_().then(() => done())
  });

  it('ignoreAll should resolve the promise', function (done) {
    let editor = stubEditor();
    editor.getEditingSupport().getOperationsInvoker()
        .invoke.returns(Promise.resolve());

    let manSpAction = new SpellcheckAction(editor);
    manSpAction.ignoreAll_().then(() => done())
  });

  it('replace should resolve the promise', function (done) {
    let editor = stubEditor();

    editor.getEditingSupport().getOperationsInvoker()
        .invoke.returns(Promise.resolve());
    let manSpAction = new SpellcheckAction(editor);
    manSpAction.showDialog_();
    manSpAction.replace_().then(() => done());
  });

  it('replace should resolve the promise when word changed', function (done) {
    let editor = stubEditor();

    let wordChangedResult = JSON.stringify({wordChanged: true});
    editor.getEditingSupport().getOperationsInvoker()
        .invoke.returns(Promise.resolve(wordChangedResult));
    let manSpAction = new SpellcheckAction(editor);
    manSpAction.showDialog_();
    manSpAction.replace_()
        .then(() => done());

    setTimeout(() => {
      let dialog = getWarnDialog();
      acceptDialog(dialog);
    })
  });

  it('findNext should resolve the promise when a problem was found', function (done) {
    let editor = stubEditor();

    let respose = JSON.stringify({word: 'xxx', suggestions: ['yyy']});
    editor.getEditingSupport().getOperationsInvoker()
        .invoke.returns(Promise.resolve(respose));
    let manSpAction = new SpellcheckAction(editor);
    manSpAction.showDialog_();
    manSpAction.findNext().then(() => done());
  });

  function getWarnDialog() {
    let warnElement = document.getElementById('word-changed-warn-container');
    return goog.dom.getAncestorByClass(warnElement, 'modal-dialog');
  }

  function acceptDialog(dialog) {
    let okButton = dialog.querySelector('[name="ok"]');
    okButton.click();
  }

  function stubEditor() {
    let editor = new sync.api.Editor();

    let spellChecker = new sync.api.SpellChecker();
    spellChecker.addIgnoredWord = goog.nullFunction;
    sinon.stub(editor, "getSpellChecker").returns(spellChecker);
    sinon.stub(editor, "getActionsManager").returns(new sync.api.ActionsManager());
    sinon.stub(editor, "getEditingSupport").returns(stubEditingSupport());
    let selectionManager = new sync.api.SelectionManager();
    selectionManager.evalSelectionFunction = goog.functions.FALSE;
    sinon.stub(editor, "getSelectionManager").returns(selectionManager);
    sinon.stub(editor, "getReadOnlyState").returns({readOnly:false});
    return editor;
  }

  /**
   * @return {sync.support.BasicAuthorSupport} a stub editing support.
   */
  function stubEditingSupport() {
    let editingSupport = new sync.support.BasicAuthorSupport(
        null, null, document.createElement('div'), {});
    let opsInvoker = {invoke: sinon.stub()};
    editingSupport.getOperationsInvoker = sinon.stub().returns(opsInvoker);
    editingSupport.scheduleDocumentTransaction = goog.nullFunction;
    return editingSupport;
  }
});
