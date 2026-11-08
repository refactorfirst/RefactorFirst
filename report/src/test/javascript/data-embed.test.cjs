const assert = require('node:assert/strict');
const { test } = require('node:test');

// T2.4: mirrors the Java DataEmbedder escape contract for inline HTML script embedding.
// `</` becomes `<\/` and Unicode line separators become `\u2028`/`\u2029` so the serialized
// report JSON can be interpolated into a <script> block and parsed back without closing the
// script early or breaking the JSON.parse round-trip.
function escapeForHtmlScript(json) {
    return json.replace(/<\//g, '<\\/').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
}

test('embedded report JSON survives </script> escaping and JSON.parse round-trip', () => {
    const hostileClassName = 'com.example.`Weird${Owner}</script>\u2028\u2029';
    const payload = JSON.stringify({ disharmonies: [{ type: 'God Class', className: hostileClassName }] });
    const embedded = escapeForHtmlScript(payload);

    const parsed = JSON.parse(embedded);
    assert.deepEqual(parsed, JSON.parse(payload));
    assert.equal(parsed.disharmonies[0].className, hostileClassName);
});

test('escaped payload contains no raw closing script sequence', () => {
    const payload = JSON.stringify({ sourceClass: 'com.example.`Weird${Owner}</script>' });
    const embedded = escapeForHtmlScript(payload);
    assert.ok(!embedded.includes('</script>'));
    assert.ok(embedded.includes('<\\/script>'));
});
