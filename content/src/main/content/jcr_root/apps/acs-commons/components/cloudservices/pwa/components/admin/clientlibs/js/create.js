/*
 * ADOBE CONFIDENTIAL
 *
 * Copyright 2016 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 */
(function(window, document, Granite, $) {
    'use strict';

    var  NS = '.cq-confadmin-create-dtm-reactor-item';

    var ui = $(window).adaptTo('foundation-ui');

    /**
     * Toggles the self-hosting fieldset
     * 
     * @param toggleElement Toggle element (i.e. checkbox)
     */
    var toggleSelfHosting = function(toggleElement) {
        // can be either a wizard step or a tab-panel
        var $parent = $(toggleElement).parents('.foundation-wizard-step, coral-panel');

        if ($(toggleElement).attr('checked') !== undefined) {
            $parent.find('.selfhosting').removeAttr('hidden');
            $parent.find('.nonselfhosting').attr('hidden', 'hidden');
        } else {
            $parent.find('.selfhosting').attr('hidden', 'hidden');
            $parent.find('.nonselfhosting').removeAttr('hidden');
        }
    }

    /**
     * Disables the archive password field
     * 
     * @param toggleElement Toggle element
     * @param disable Flag to disable field
     */
    var disableArchivePassword = function(toggleElement, disable) {
        if (disable) {
            $(toggleElement).attr('disabled','disabled');
        } else {
            $(toggleElement).removeAttr('disabled');
        }
    }

    /**
     * Toggles the scheduler expression
     * 
     * @param toggleElement Toggle element (i.e. checkbox)
     */
    var toggleSchedulerExpression = function(toggleElement) {
        // can be either a wizard step or a tab-panel
        var $parent = $(toggleElement).parents('.foundation-wizard-step, coral-panel');

        if ($(toggleElement).attr('checked') !== undefined) {
            $parent.find('[name$=schedulerExpression]').parent('div').removeAttr('hidden');
        } else {
            $parent.find('[name$=schedulerExpression]').parent('div').attr('hidden', 'hidden');
            $parent.find('[name$=schedulerExpression]').val('');
        }
    }

    var prefillAutoCompleteSource = function() {
        var $imsConfigId = $(NS + ' [name$=imsConfigId]');
        var $companyId = $(NS + ' [name$=company]');
        var imsConfigId = $imsConfigId.length > 0 ? $imsConfigId.get(0).value : undefined;
        var companyId = $companyId.length > 0 ? $companyId.get(0).value : undefined;

        var autocompletes = $(NS + ' [data-granite-autocomplete-src]');
        $.each(autocompletes, function(idx,el){
            var src = $(el).attr('data-granite-autocomplete-src');
            if (src) {
                if (imsConfigId) {
                    src = src.replace(/imsConfigurationId=[^\&]*\&/, 'imsConfigurationId=' + encodeURIComponent(imsConfigId) + '\&');
                }
                if (companyId) {
                    src = src.replace(/companyId=[^\&]*\&/, 'companyId=' + companyId + '\&');
                }
                $(el).attr('data-granite-autocomplete-src', src);
            }
        });
    }

    $(document).on('foundation-contentloaded', function(e) {
        // Enable AutoComplete for edit use-case
        var $imsConfigId = $(NS + ' coral-select[name$=imsConfigId]');
        if ($imsConfigId.length > 0) {
            Coral.commons.ready($imsConfigId.get(0), function(element) {
                prefillAutoCompleteSource();
            });
        }

        // Pretty-print AutoCompletes
        var companyLabel = $(NS + ' [name$=companyLabel]').val();
        var $companyAutocomplete = $(NS + ' .cq-confadmin-dtm-reactor-companylist');
        if ($companyAutocomplete) {
            Coral.commons.ready($companyAutocomplete.get(0), function() {
                 if (companyLabel) {
                     $(NS + ' .cq-confadmin-dtm-reactor-companylist input[role=textbox]').val(companyLabel);
                 }
            })
        }

        var propertyLabel = $(NS + ' [name$=propertyLabel]').val();
        var $propertyAutocomplete = $(NS + ' .cq-confadmin-dtm-reactor-propertylist');
        if ($propertyAutocomplete) {
            Coral.commons.ready($propertyAutocomplete.get(0), function() {
                if (propertyLabel) {
                    $(NS + ' .cq-confadmin-dtm-reactor-propertylist input[role=textbox]').val(propertyLabel);
                }
            });
        }

        // read-only fields - granite doesn't support setting the readonly
        // attribute, so set it after loading the UI content
        $(NS + ' [name$=libraryUri],' + NS + ' [name$=downloadLink]').attr('readonly', 'true');

        // override onClick since disabled would prevent submission of its value
        $(NS + ' coral-switch[name$=archive]').on('click', function(){return false;});

        // toggle self hosting
        $(NS + ' coral-switch[name$=archive]').each(function(idx,el){
            toggleSelfHosting(el);
        });

        // toggle archive password
        $(NS + ' [name$=archivePassword]').each(function(idx,el){
            var disable = $(el).closest('.properties').find('[type=hidden][name$=archiveEncrypted]').val() === 'false';
            disableArchivePassword(el, disable);
        });

        // toggle scheduler expression
        $(NS + ' coral-switch[name$=enablePollingImporter]').each(function(idx, el){
            toggleSchedulerExpression(el);
        });
    });

    // Toggle Scheduler Expression
    $(document).on('change', NS + ' [name$=enablePollingImporter]', function() {
        toggleSchedulerExpression(this);
    });

    // update any field that refers to the imsconfig id in their data src.
    $(document).on('change', NS + ' .cq-confadmin-dtm-reactor-imsconfiglist', function() {
        prefillAutoCompleteSource();
    });

    // update any field that refers to the companyId in their data src
    $(document).on("change:value", NS + ' .cq-confadmin-dtm-reactor-companylist.coral-Autocomplete:not(coral-autocomplete)', function(evt,value) {
        $(NS + ' [name$=companyLabel]').val($(evt.currentTarget).find('[role=textbox]').get(0).value || '');
        prefillAutoCompleteSource();
    });

    // preload the data from the selected property
    $(document).on("change:value", NS + ' .cq-confadmin-dtm-reactor-propertylist.coral-Autocomplete:not(coral-autocomplete)', function(evt,value) {
        $(NS + ' [name$=propertyLabel]').val($(evt.currentTarget).find('[role=textbox]').get(0).value || '');

        ui.wait();

        var imsConfigId = $(NS + ' coral-select[name$=imsConfigId]').get(0).value;
        var propertyId = $(NS + ' [name$=property]').get(0).value;

        if (imsConfigId && propertyId) {
            $.ajax({
                method: 'GET',
                url: Granite.HTTP.externalize('/apps/acs-commons/components/cloudservices/pwa/content/configurations/createcloudconfigwizard/environmentsData'),
                data: {
                    imsConfigurationId: imsConfigId,
                    propertyId: propertyId
                }
            }).done(function(data,status,xhr) {
                    if (data) {
                        for (var index = 0; index < data.length; index++) {
                            var entry = data[index];
                            var targetTab = undefined;
                            if ('staging' === entry['environment']) {
                                targetTab = './staging';
                            } else if ('production' === entry['environment']) {
                                targetTab = './production';
                            }

                            $(NS + ' [name="./' + entry['environment'] + '/environment"]')
                                .val(entry['id'] !== undefined ? entry['id'] : '');

                            if (targetTab) {
                                // archive switch
                                var archive   = $(NS + ' coral-switch[name="' + targetTab + '/archive"]');
                                var archiveHidden = $(NS + ' input[name="' + targetTab + '/archive"]');
                                if (entry['download_link'] !== undefined) {
                                    archive.attr('checked', 'checked');
                                    archiveHidden.val('true');
                                } else {
                                    archive.removeAttr('checked');
                                    archiveHidden.val('false');
                                }
                                // archive encryption
                                $(NS + ' [name="' + targetTab + '/archiveEncrypted"]')
                                    .val(entry['archive_encrypted']);

                                $(NS + ' .nonselfhosting [name="' + targetTab + '/libraryUri"]')
                                    .val(entry['library_uri'] !== undefined ? entry['library_uri'] : '');

                                $(NS + ' .selfhosting [name="' + targetTab + '/downloadLink"]')
                                    .val(entry['download_link'] !== undefined ? entry['download_link'] : '');
                                $(NS + ' .selfhosting [name="' + targetTab + '/domainHint"]')
                                    .val(entry['domain_hint'] !== undefined ? entry['domain_hint'] : '');


                                toggleSelfHosting(archive);

                                disableArchivePassword(
                                    $(NS + ' .selfhosting [name="' + targetTab + '/archivePassword"]'),
                                    (entry['archive_encrypted'] == false)
                                );
                            }
                        }
                    }
            }).fail(function(xhr,status,error) {
                ui.alert(
                    Granite.I18n.get('Error'),
                    Granite.I18n.get('Unable to retrieve environments from Adobe Launch.'),
                    "error"
                );
            }).always(function() {
                ui.clearWait();
            });
        }
    });

    $(window).adaptTo("foundation-registry").register("foundation.form.submit", {
        selector: ".cq-confadmin-create-dtm-reactor-item",
        handler: function(form) {
            var deferred = $.Deferred();

            var stgArchive = $(NS + ' input[type="hidden"][name="./staging/archive"]').val();
            var prdArchive = $(NS + ' input[type="hidden"][name="./production/archive"]').val();

            var data = {
                imsConfiguration: $(NS + ' [name$=imsConfigId]').get(0).value,
                property: $(NS + ' [name$=property]').get(0).value,
                developmentEnvironment: $(NS + ' [name="./development/environment"]').val(),
                stagingArchive: (stgArchive == 'true'),
                stagingEnvironment: $(NS + ' [name="./staging/environment"]').val(),
                stagingDomainHint: $(NS + ' [name="./staging/domainHint"]').val(),
                productionArchive: (prdArchive == 'true'),
                productionEnvironment: $(NS + ' [name="./production/environment"]').val(),
                productionDomainHint: $(NS + ' [name="./production/domainHint"]').val()
            };

            // check for at least one environment with archive
            if (stgArchive == 'true' || prdArchive == 'true') {
                $.post({
                   url: Granite.HTTP.externalize('/apps/acs-commons/components/cloudservices/pwa/content/configurations/createcloudconfigwizard/jcr:content.updateEnvironments.html'),
                   data: data
                }).done(function(data,status,xhr) {
                    deferred.resolve();
                }).fail(function(xhr,status,error) {
                    var el = document.createElement('html');
                    el.innerHTML = xhr.responseText;

                    var msg = xhr.responseText !== '' ? $('body > h1', $(el)).text() : Granite.I18n.get('An unknown error occurred.');
                    if (msg === 'Unable to update environments') {
                        ui.alert(
                            Granite.I18n.get('Error'),
                            Granite.I18n.get('Unable to update environments in Adobe Launch.'),
                            "error"
                        );
                        deferred.reject();
                    }
                    else if (msg === 'Unable to publish changes') {
                        ui.prompt(
                            Granite.I18n.get('Unable to publish changes'),
                            Granite.I18n.get(
                                'Please resolve publish conflicts in Adobe Launch and<br>' +
                                're-trigger the download workflow manually afterwards.'
                            ),
                            "notice",
                            [
                                {
                                    text: Granite.I18n.get("Cancel"),
                                    id: "no"
                                },
                                {
                                    text: Granite.I18n.get("Save"),
                                    primary: true,
                                    id: "yes",
                                }
                            ],
                            function(id, action){
                                if(id === 'yes') {
                                    deferred.resolve();
                                } else {
                                    deferred.reject();
                                }
                            }
                        );
                    }
                    
                }).always(function(){
                    ui.clearWait();
                });
            } else {
                deferred.resolve();
            }

            return {
                preResult: deferred.promise()
            };
        }
    });

})(window, document, Granite, Granite.$);