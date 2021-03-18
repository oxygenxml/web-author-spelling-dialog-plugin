describe('ManualSpellcheckingTest', function() {
  it('action should call callback immediately', function (done) {
    let editor = new sync.api.Editor();
    sinon.stub(editor, "getActionsManager").returns(new sync.api.ActionsManager());
    sinon.stub(editor, "getSpellChecker").returns(new sync.api.SpellChecker());

    let manSpAction = new SpellcheckAction(editor);
    let cb = sinon.spy();
    manSpAction.actionPerformed(cb);
    assert(cb.callCount === 1);
    done();
  });
});
