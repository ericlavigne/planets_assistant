# vgap

Tools for playing VGA Planets at http://planets.nu

## Usage

### Development mode

To run the development server, run

```
lein figwheel
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

#### Optional development tools

Start the browser REPL:

```
$ lein repl

(browser-repl)
```

#### Run tests

```
$ lein test
```

#### Run script

```
$ lein run
```

### Building for release

```
lein cljsbuild clean
lein ring uberjar
```
