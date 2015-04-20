/*global require, module, process */

module.exports = {
  extractConfig: extractConfig
};

var promise = require('q'),
    fs = require('fs'),
    spawn = require('child_process').spawn,
    StringDecoder = require('string_decoder').StringDecoder,
    decoder = new StringDecoder('utf8'),
    log4js = require('log4js'),
    cache = {},
    buffer = '';

var log = log4js.getLogger('default');

/*
 *  Extracts the config
 *  @returns {promise}
 */

function extractConfig(param) {
  var deferred = promise.defer(),
      tool;

  param = param || 'cdap';

  if (cache[param]) {
    deferred.resolve(cache[param]);
    return deferred.promise;
  }

  if (process.env.NODE_ENV === 'production') {
    buffer = '';
    tool = spawn(__dirname + '/../../../bin/config-tool', ['--'+param]);
    tool.stderr.on('data', configReadFail.bind(this));
    tool.stdout.on('data', configRead.bind(this));
    tool.stdout.on('end', onConfigReadEnd.bind(this, deferred, param));
  } else {
    try {
      cache[param] = require('../../../conf/generated/cdap-config.json');
    } catch(e) {
      // Indicates the backend is not running in local environment and that we want only the
      // UI to be running. This is here for convenience.
      log.info('Using development configuration for' + '"' + param + '"');
      cache[param] = require('./development/'+param+'.json');
    }

    deferred.resolve(cache[param]);
  }
  return deferred.promise;
}

function onConfigReadEnd (deferred, param) {
   cache[param] = JSON.parse(buffer);
   deferred.resolve(cache[param]);
}

function configRead (data) {
  var textChunk = decoder.write(data);
  if (textChunk) {
    buffer += textChunk;
  }
}

function configReadFail (data) {
  var textChunk = decoder.write(data);
  if (textChunk) {
    log.error('Failed to extract configuration');
  }
}
