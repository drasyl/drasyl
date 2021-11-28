if (window.location.pathname.indexOf('/master') == 0) {
	document.getElementsByTagName('h1')[0].outerHTML +='<div class="admonition important"><p class="admonition-title">Nightly Version</p><p>You\'re currently on a nightly version branch. If you\'re only interested  in the latest stable version, please click <a href="/">here</a>.</p></div>';
} 