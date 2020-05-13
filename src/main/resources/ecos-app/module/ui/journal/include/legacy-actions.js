(function () {
    return [
        {
            buttonClass: 'custom-button-edit',
            onclick: {
                fn: function () {
                    Citeck.forms.eform(oRecord.getData('nodeRef'), {});
                }
            }
        },
        {
            buttonClass: 'custom-button-download',
            onclick: {
                fn: function () {
                    var record = Citeck.Records.get(oRecord.getData('nodeRef'));
                    record.load({moduleId: 'moduleId', json: '.json'}, true).then(data => {
                        Citeck.utils.downloadText(JSON.stringify(data.json), data.moduleId + '.json', 'text/json');
                    });
                }
            }
        },
        {
            buttonClass: 'custom-button-remove',
            onclick: {
                fn: function () {

                    var recordId = oRecord.getData('nodeRef');

                    Alfresco.util.PopupManager.displayPrompt({
                        title: Alfresco.util.message("message.confirm.delete.title"),
                        text: Alfresco.util.message("message.confirm.delete"),
                        buttons: [
                            {
                                text: Alfresco.util.message("button.delete"),
                                handler: function () {
                                    this.destroy();
                                    Citeck.Records.remove([recordId]);
                                }
                            },
                            {
                                text: Alfresco.util.message("button.cancel"),
                                handler: function () {
                                    this.destroy();
                                },
                                isDefault: true
                            }
                        ]
                    });
                }
            }
        }
    ];
    }
)();
