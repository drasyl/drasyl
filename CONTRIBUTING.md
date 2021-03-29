# Contributing to drasyl

This is a short guide on how to contribute things to drasyl.

## Reporting a bug

When filing an issue, please include the following information if possible as well as a description
of the problem. Make sure you test with
the [latest snapshot version of drasyl](https://docs.drasyl.org/master/getting-started/quick-start/):

* drasyl version
* Expected behavior
* Actual behavior
* Steps to reproduce
    * Bonus points: provide a minimal working example

### Important: "Getting Help Vs Reporting an Issue"

The issue tracker is not a general support forum, but a place to report bugs and asks for new
features.

For end-user related support questions, try using first:

- the drasyl gitter
  room: [![Gitter](https://badges.gitter.im/drasyl-overlay/drasyl.svg)](https://gitter.im/drasyl-overlay/drasyl)

## Any contributions you make will be under the [MIT License](./LICENSE)

In short, when you submit code changes, your submissions are assumed to be under the same MIT
license that covers the project. Feel free to contact the maintainers if this is an issue for you.

## Submitting a pull request/merge request

If you find a bug that you'd like to fix, or a new feature that you'd like to implement then please
submit a pull request/merge request.

If it is a big feature then make an issue first so it can be discussed.

First, create a fork via GitHub's/GitLab's Web Interface.

Now in your terminal, git clone your fork.

And get hacking.

Make sure you

* Use the drasyl code style:
    * [.editorconfig](.editorconfig)
    * Use `final` keyword where possible.
    * Each file must have the following copyright notice in the header:

```
Copyright (c) 2020-$today.year Heiko Bornholdt and Kevin Röbert

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.
```

* Add [changelog](./CHANGELOG.md) entry
* Add documentation for a new feature.
* Add tests for a new feature.
* squash commits down to one per feature.
* rebase to master with `git rebase master`
* keep your pull request/merge request as small as possible.
* Make sure that `mvn checkstyle:check` does not print warnings for checkstyle

When ready - run the tests

    mvn test

When you are done with that git push your changes.

Go to the GitHub/GitLab website and click "New pull request/merge request".

Your patch will get reviewed and you might get asked to fix some stuff.

If so, then make the changes in the same branch, squash the commits (make multiple commits one
commit) by running:

```
git log # See how many commits you want to squash
git reset --soft HEAD~2 # This squashes the 2 latest commits together.
git status # Check what will happen, if you made a mistake resetting, you can run git reset 'HEAD@{1}' to undo.
git commit # Add a new commit message.
git push --force # Push the squashed commit to your fork repo.
```

## Making a release ##

There are separate instructions for making a release in the [RELEASE](RELEASE.md)
file.
