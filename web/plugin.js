(function () {
 goog.events.listenOnce(workspace, sync.api.Workspace.EventType.BEFORE_EDITOR_LOADED, 
     function(e) {
   var editor = e.editor;

   // Register the newly created action.
   editor.getActionsManager().registerAction('spellcheck', new SpellcheckAction(editor));
   addToMoreToolbar(editor, 'spellcheck');	  
 });


 /**
  * The action that shows a popup and then inserts the text in the pop-up.
  */
 SpellcheckAction = function(editor) {
   sync.actions.AbstractAction.call(this, 'M1 F7');
   this.editor = editor;
 };
 // shortcut is Meta+L on Mac and Ctrl+L on other platforms.
 SpellcheckAction.prototype = Object.create(sync.actions.AbstractAction.prototype);
 SpellcheckAction.prototype.constructor = SpellcheckAction;

 SpellcheckAction.prototype.getDisplayName = function() {
   return 'Spellcheck';
 };

 // The actual action execution.
 SpellcheckAction.prototype.actionPerformed = function(callback) {
   this.editor.getActionsManager().invokeOperation(
     'com.oxygenxml.webapp.plugins.spellcheck.GoToNextSpellingErrorOperation', {
     'fromCaret' : true,
   }, function(err, resultString) {
     var selectedNode = window.getSelection().focusNode;
     if (selectedNode) {
       if (selectedNode.nodeType === 3) {
         selectedNode = selectedNode.parentNode;
       }
       selectedNode.scrollIntoView();
     }
	   var result = JSON.parse(resultString);
	   console.log(result)
     callback && callback();
   });

 };


 function addToMoreToolbar(editor, actionId) {
   goog.events.listen(editor, sync.api.Editor.EventTypes.ACTIONS_LOADED, function(e) {
     var actionsConfig = e.actionsConfiguration;

     var builtinToolbar = null;
     if (actionsConfig.toolbars) {
       for (var i = 0; i < actionsConfig.toolbars.length; i++) {
         var toolbar = actionsConfig.toolbars[i];
         console.log(toolbar.name)
         if (toolbar.name == "Builtin") {
           builtinToolbar = toolbar;
         }
       }
     }

     if (builtinToolbar) {
       builtinToolbar.children.push({
         id: actionId,
         type: "action"
       });
     }
   });
 }
})();
