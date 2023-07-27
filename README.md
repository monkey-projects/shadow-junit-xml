# MonkeyProjects Shadow-cljs JUnit XML Reporter

This is a [ClojureScript](https://cljs.info) library that provides a
reporter for [Shadow-cljs](https://github.com/thheller/shadow-cljs)
unit tests that outputs as [JUnit](https://junit.org) xml.  This
makes it useful for improving test reporting in various build pipeline
tools.

## Why?

"Doesn't this already exist somewhere?" I hear you say.  I thought so
too, but I could not find any library that allowed this.  Yes, there
is [Kaocha](https://github.com/lambdaisland/kaocha), but as far as I
can tell, it doesn't provide support for JUnit in cljs, when running
it as a plain Node script.  Also, the [cljs2](https://github.com/lambdaisland/kaocha-cljs2)
adds [a lot of complexities](https://github.com/lambdaisland/funnel)
way beyond just running it as plain JavaScript.  This is not really
a problem when running it locally.  In fact, this lib also provides
a configuration for doing just that.  But in a CI/CD pipeline, having
to start an additional service just to be able to output JUnit xml
was going a bit too far for me.

## Usage

It's quite simple.  Actually, this library [uses it too](shadow-cljs.edn).
I assume you've already [set up your Shadow-cljs project](https://shadow-cljs.github.io/docs/UsersGuide.html#_usage_.
Next, add a build that targets `node-test` and use this library's test
runner as your `main`, like this:

```clojure
{:builds
 {:test-ci
  {:target :node-test
   :output-to "target/js/node-tests.js"
   ;; Output as junit xml
   :main monkey.shadow.junit.runner/run-tests}}}
```

Then compile the code:
```bash
$ npx shadow-cljs compile test-ci
```
This will generate a JavaScript file as configured in the `output-to` property.
Then run it using [nodejs](https://nodejs.org/):
```bash
$ node target/js/node-tests.js
```

This will run your unit tests and output the result as JUnit xml to `stdout`.
In order to save it to a file, redirect `stdout`:
```bash
$ node target/js/node-tests.js 1>junit.xml
```
In the above example, `junit.xml` should now contain the test results in JUnit
format, which you can then pass on to your CI/CD tool!