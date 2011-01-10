# Poolradwheel

In the late 80's SSI Published a series of AD&D computer games
starting with _Pool of Radiance_ [1].  Some of these came with a
decoder Wheel required to enter the game and at some points during
game play.  This program simlates the decoder wheel as required for
entering the game.

[1]: http://en.wikipedia.org/wiki/Pool_of_Radiance

In its present form, it doesn't implement glyph-at-a-time translation
which is occasionally required during game play.

## Building

You'll need a recent copy of Maven installed in order to build this.
Running `mvn package` will deposit a "jar-with-dependencies" in the
`target` directory:

    $ mvn package
    ...
    ... [INFO] blah blah blah maven blah blah
    ...
    $ java -jar target/PoolradWheel-1.0-SNAPSHOT-jar-with-dependencies.jar

### convert-images.sh

Source images for the glyphs are in src/images. This script scales
them down from 256x256 to 24x24 and copies them to src/main/resources
using [ImageMagic](http://www.imagemagick.org/script/index.php).
