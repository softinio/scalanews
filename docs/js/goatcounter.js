// GoatCounter analytics (https://wisdom.softinio.com), shared with
// www.softinio.com and watch.softinio.com. Each path is prefixed with the
// hostname so the sites' pages stay apart in the one GoatCounter site.
// count.js is injected from here rather than listed as a second script, so
// these settings are always in place before it runs. count.js never counts
// localhost, so the local preview server is not counted.
(function () {
  window.goatcounter = {
    path: function (p) { return location.host + p; }
  };

  var script = document.createElement('script');
  script.async = true;
  script.src = 'https://wisdom.softinio.com/count.js';
  script.setAttribute('data-goatcounter', 'https://wisdom.softinio.com/count');
  document.head.appendChild(script);
})();
