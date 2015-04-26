# vgap

Tools for playing VGA Planets at http://planets.nu

## Setup

### Datomic

For now, the application assumes that a Datomic instance is running locally.

1. Register for [Datomic Pro Starter license](https://my.datomic.com).
2. Download and unzip [Datomic Pro](https://my.datomic.com/downloads/pro).
3. Configure Datomic [dev-storage](http://docs.datomic.com/getting-started.html#dev-storage).
4. Add these lines to ~/.bashrc then source the file. Use the correct username and download key from [these instructions](https://my.datomic.com/account).

    > export DATOMIC_USERNAME="username@email.com"
    > export DATOMIC_DOWNLOAD_KEY="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"

    $ source ~/.bashrc

5. Run the transactor.

    $ ./bin/transactor dev-transactor.properties

6. Run the console (separate terminal).

    $ ./bin/console -p 8080 dev datomic:dev://localhost:4334/

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
