/* global cordova:false */
/* globals window */

/*!
 * Module dependencies.
 */

var exec = cordova.require('cordova/exec');

/**
 * Geofence constructor.
 *
 * @param {Object} options to initiate Push Notifications.
 * @return {Geofence} instance that can be monitored and cancelled.
 */
var Geofence = function (options) {
    this._handlers = {
        transition: [],
        error: []
    };

    // require options parameter
    if (typeof options === 'undefined') {
        throw new Error('The options argument is required.');
    }

    var that = this;

    /**
     * Emit an event.
     *
     * This is intended for internal use only.
     *
     * @param {String} eventName is the event to trigger.
     * @param {*} all arguments are passed to the event listeners.
     *
     * @return {Boolean} is true when the event is triggered otherwise false.
     */
    function emit() {
        var args = Array.prototype.slice.call(arguments);
        var eventName = args.shift();

        if (!that._handlers.hasOwnProperty(eventName)) {
            return false;
        }

        for (var i = 0, length = that._handlers[eventName].length; i < length; i++) {
            var callback = that._handlers[eventName][i];
            if (typeof callback === 'function') {
                callback.apply(undefined, args);
            } else {
                console.log('event handler: ' + eventName + ' must be a function');
            }
        }

        return true;
    }

    function executeFunctionByName(functionName, context) {
        var args = Array.prototype.slice.call(arguments, 2);
        var namespaces = functionName.split('.');
        var func = namespaces.pop();

        for (var i = 0; i < namespaces.length; i++) {
            context = context[namespaces[i]];
        }

        return context[func].apply(context, args);
    }

    // triggered on notification
    var success = function (result) {
        if (result) {
            if (result.callback) {
                executeFunctionByName(result.callback, window, result);
            } else if (result.ids) {
                emit('transition', result);
            }
        }
    };

    // triggered on error
    var fail = function (msg) {
        var e = (typeof msg === 'string') ? new Error(msg) : msg;
        emit('error', e);
    };

    // wait at least one process tick to allow event subscriptions
    setTimeout(function () {
        exec(success, fail, 'Geofence', 'init', [options]);
    }, 10);
};

Geofence.prototype.addFences = function (fences, successCallback, errorCallback) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function (error) {
            console.log('Geofence Error: ' + JSON.stringify(error));
        };
    }

    if (typeof errorCallback !== 'function') {
        console.log('Geofence.addFences failure: failure parameter not a function');
        return;
    }

    if (typeof successCallback !== 'function') {
        console.log('Geofence.addFences failure: success callback parameter must be a function');
        return;
    }

    if (!(fences instanceof Array)) {
        fences = [fences];
    }

    exec(successCallback, errorCallback, 'Geofence', 'addFences', [fences]);
};

Geofence.prototype.removeFences = function (ids, successCallback, errorCallback) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function (error) {
            console.log('Geofence Error: ' + JSON.stringify(error));
        };
    }

    if (typeof errorCallback !== 'function') {
        console.log('Geofence.removeFences failure: failure parameter not a function');
        return;
    }

    if (typeof successCallback !== 'function') {
        console.log('Geofence.removeFences failure: success callback parameter must be a function');
        return;
    }

    if (!(ids instanceof Array)) {
        ids = [ids];
    }

    exec(successCallback, errorCallback, 'Geofence', 'removeFences', [ids]);
};

Geofence.prototype.removeAllFences = function (successCallback, errorCallback) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function (error) {
            console.log('Geofence Error: ' + JSON.stringify(error));
        };
    }

    if (typeof errorCallback !== 'function') {
        console.log('Geofence.removeAllFences failure: failure parameter not a function');
        return;
    }

    if (typeof successCallback !== 'function') {
        console.log('Geofence.removeAllFences failure: success callback parameter must be a function');
        return;
    }

    exec(successCallback, errorCallback, 'Geofence', 'removeAllFences', []);
};

Geofence.prototype.clearAllNotifications = function (successCallback, errorCallback) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function (error) {
            console.log('Geofence Error: ' + JSON.stringify(error));
        };
    }

    if (typeof errorCallback !== 'function') {
        console.log('Geofence.clearAllNotifications failure: failure parameter not a function');
        return;
    }

    if (typeof successCallback !== 'function') {
        console.log('Geofence.clearAllNotifications failure: success callback parameter must be a function');
        return;
    }

    exec(successCallback, errorCallback, 'Geofence', 'clearAllNotifications', []);
};

Geofence.prototype.getFence = function (id, successCallback, errorCallback) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function (error) {
            console.log('Geofence Error: ' + JSON.stringify(error));
        };
    }

    if (typeof errorCallback !== 'function') {
        console.log('Geofence.getFence failure: failure parameter not a function');
        return;
    }

    if (typeof successCallback !== 'function') {
        console.log('Geofence.getFence failure: success callback parameter must be a function');
        return;
    }

    if (!id) {
        console.log('Geofence.getFence failure: id must be defined');
        return;
    }

    exec(successCallback, errorCallback, 'Geofence', 'getFence', [id]);
};

Geofence.prototype.getFences = function (successCallback, errorCallback) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function (error) {
            console.log('Geofence Error: ' + JSON.stringify(error));
        };
    }

    if (typeof errorCallback !== 'function') {
        console.log('Geofence.getFences failure: failure parameter not a function');
        return;
    }

    if (typeof successCallback !== 'function') {
        console.log('Geofence.getFences failure: success callback parameter must be a function');
        return;
    }

    exec(successCallback, errorCallback, 'Geofence', 'getFences', []);
};

/**
 * Listen for an event.
 *
 * The following events are supported:
 *
 *   - transition
 *   - error
 *
 * @param {String} eventName to subscribe to.
 * @param {Function} callback triggered on the event.
 */

Geofence.prototype.on = function (eventName, callback) {
    if (this._handlers.hasOwnProperty(eventName)) {
        this._handlers[eventName].push(callback);
    }
};

/**
 * Remove event listener.
 *
 * @param {String} eventName to match subscription.
 * @param {Function} handle function associated with event.
 */
Geofence.prototype.off = function (eventName, handle) {
    if (this._handlers.hasOwnProperty(eventName)) {
        var handleIndex = this._handlers[eventName].indexOf(handle);
        if (handleIndex >= 0) {
            this._handlers[eventName].splice(handleIndex, 1);
        }
    }
};

Geofence.prototype.finish = function (successCallback, errorCallback, id) {
    if (!successCallback) {
        successCallback = function () {
        };
    }
    if (!errorCallback) {
        errorCallback = function () {
        };
    }
    if (!id) {
        id = 'handler';
    }

    if (typeof successCallback !== 'function') {
        console.log('finish failure: success callback parameter must be a function');
        return;
    }

    if (typeof errorCallback !== 'function') {
        console.log('finish failure: failure parameter not a function');
        return;
    }

    exec(successCallback, errorCallback, 'Geofence', 'finish', [id]);
};

/*!
 * Push Notification Plugin.
 */

module.exports = {
    /**
     * Register for Push Notifications.
     *
     * This method will instantiate a new copy of the Geofence object
     * and start the registration process.
     *
     * @param {Object} options
     * @return {Geofence} instance
     */

    init: function (options) {
        return new Geofence(options);
    },

    hasPermission: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'Geofence', 'hasPermission', []);
    },

    /**
     * Geofence Object.
     *
     * Expose the Geofence object for direct use
     * and testing. Typically, you should use the
     * .init helper method.
     */
    Geofence: Geofence,

    /**
     * TransitionTypes Object.
     *
     * Expose the TransitionTypes object
     */
    TransitionTypes: {
        ENTER: 1,
        EXIT: 2,
        DWELL: 4
    }
};
