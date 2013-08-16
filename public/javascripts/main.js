'use strict';

$(window).ready(function() {
    /**
     * The model for the Reactive Manifesto pages
     */
    function ReactiveManifestoModel() {
        var self = this;

        /**
         * The total number of people that have signed the manifesto
         */
        self.total = ko.observable();
        /**
         * The current list of signatories that we have downloaded
         */
        self.signatories = ko.observableArray();
        /**
         * The current logged in user
         */
        self.loggedInUser = ko.observable();
        /**
         * The URL to fetch more, if there are more signatories to display
         */
        self.fetchMore = ko.observable();

        /**
         * Invoked when the user clicks sign the manifesto
         */
        self.signTheManifesto = function(data, e) {
            e.preventDefault();
            $("body").addClass("signing");
        };

        /**
         * Invoked when the user clicks logout
         */
        self.logOut = function() {
            $.ajax({type: "DELETE", url: "/user"}).done(function() {
                self.loggedInUser(null)
            });
        };

        /**
         * Invoked when a logged in user clicks the sign button
         */
        self.sign = function() {
            $.ajax({type: "PUT", url: "/user/sign"}).done(function(data) {
                self.loggedInUser(data);
                self.refreshTotal();
                self.refreshSignatories(30);
            });
        };

        /**
         * Invoked when a logged in user clicks the unsign button
         */
        self.unsign = function() {
            $.ajax({type: "DELETE", url: "/user/sign"}).done(function(data) {
                self.loggedInUser(data);
                self.refreshTotal();
                self.refreshSignatories(30);
            });
        };

        /**
         * Invoked when a user chooses to log in using Twitter
         */
        self.logInTwitter = function() {
            window.open("/twitter/auth", "reactivemanifestologin", "width=1024,height=640")
        };

        /**
         * Invoked when a user chooses to log in using GitHub
         */
        self.logInGitHub = function() {
            window.open("/github/auth", "reactivemanifestologin", "width=1024,height=640")
        };

        /**
         * Invoked when a user chooses to log in using Google
         */
        self.logInGoogle = function() {
            window.open("/google/auth", "reactivemanifestologin", "width=1024,height=640")
        };

        /**
         * Invoked when a user chooses to log in using LinkedIn
         */
        self.logInLinkedIn = function() {
            window.open("/linkedin/auth", "reactivemanifestologin", "width=1024,height=640")
        };

        /**
         * Refresh the current user
         */
        self.refreshUser = function() {
            $.ajax("/user").done(self.loggedInUser);
        };

        /**
         * Refresh the total number of signatories
         */
        self.refreshTotal = function() {
            $.ajax("/signatories/total").done(function(data) {
                self.total(data.total);
            });
        };

        /**
         * Refresh the list of signatories
         *
         * @param number The number of signatories to fetch
         */
        self.refreshSignatories = function(number) {
            $.ajax("/signatories?per_page=" + number).done(function(data, status, xhr) {
                for (var i = 0; i < data.length; i++) {
                    processSigned(data[i])
                }
                self.signatories(data);
                handleLink(xhr);
            });
        };

        /**
         * Handle the link header from the signatories response, extracting out the rel="next" URL, if present.
         */
        function handleLink(xhr) {
            var link = xhr.getResponseHeader("Link");
            if (link != undefined) {
                // Extract the next page from the header
                self.fetchMore(/.*<([^>]*)>; rel="next".*/.exec(link)[1])
            } else {
                self.fetchMore(null)
            }
        }

        /**
         * Work out how long ago the person signed the manifesto in human readable English
         */
        function processSigned(person) {
            var now = new Date().getTime()
            var signed = person.signed
            var ago = now - signed
            // Work out minutes
            var minutes = Math.floor(ago / 60000)

            function inEnglish(time, singular, plural) {
                if (time <= 1) {
                    return singular + " ago"
                } else {
                    return time + " " + plural + " ago"
                }
            }

            if (minutes < 60) {
                person.fromNowSigned = inEnglish(minutes, "a minute", "minutes")
            } else {
                var hours = Math.floor(minutes / 60)
                if (hours < 24) {
                    person.fromNowSigned = inEnglish(hours, "an hour", "hours")
                } else {
                    var days = Math.floor(hours / 24)
                    if (days < 365) {
                        person.fromNowSigned = inEnglish(days, "a day", "days")
                    } else {
                        person.fromNowSigned = inEnglish(Math.floor(days / 365), "a year", "years")
                    }
                }
            }
            return person
        }

        /**
         * Fetch more users
         */
        self.doFetchMore = function() {
            $.ajax(self.fetchMore()).done(function(data, status, xhr) {
                for (var i = 0; i < data.length; i++) {
                    processSigned(data[i])
                }
                // Don't push each one individually, the browser will have a heart attack
                self.signatories(self.signatories().concat(data))
                handleLink(xhr)
            });
        }
    }

    var model = new ReactiveManifestoModel();
    ko.applyBindings(model);

    model.refreshTotal();
    if (location.pathname == "/list") {
        model.refreshSignatories(200)
    } else {
        model.refreshSignatories(30);

    }
    model.refreshUser();

    // The callback that the OAuth popup will call once the user has successfully logged in via a third party service.
    window.refreshUser = model.refreshUser;

});
