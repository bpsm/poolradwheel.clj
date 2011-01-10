;;;   Copyright (c) 2011, B. Smith-Mannschott
;;;   All rights reserved.
;;;   License: BSD-Style (see LICENSE)

(ns poolradwheel
  (:import [java.awt Image GridLayout GraphicsConfiguration]
           [java.awt.event  ItemEvent ItemListener]
           [javax.swing ImageIcon Box ButtonGroup JComponent JFrame JLabel
            JPanel JRadioButton JToggleButton SwingConstants SwingUtilities]
           [javax.swing.event ChangeEvent ChangeListener])
  (:require [clojure.java.io :as io])
  (:gen-class))

;;; ======================================================================
;;; In the late 80's SSI Published a series of AD&D computer games
;;; starting with _Pool of Radiance_ [1].  Some of these came with a
;;; decoder Wheel required to enter the game and at some points during
;;; game play.  This program simlates the decoder wheel as required for
;;; entering the game.
;;;
;;; [1] http://en.wikipedia.org/wiki/Pool_of_Radiance
;;;
;;; In 2003, I decided it might be fun to write a program to simulate the
;;; decoder. I did so, in Java.  I recently (2011) dusted it off and rewrote
;;; it in Clojure for the exercise.
;;;
;;; This implementation is about half the length (in lines, words,
;;; characters) of the Java original.  Some of that difference is due
;;; to my implementing concepts Game, Spiral, Dethek and Espuar using
;;; the "Type-Safe Enum Pattern" to gain static type safety.  This
;;; implementation, being dynamically typed drops all this ceremony
;;; and just represents these enumerations as integers.
;;;
;;; I estimate that simplify the Java implementation accordingly would
;;; reduce the length of the Java implementation by about a quarter.
;;; ======================================================================

;; We simulate three decoder wheels, one for each of the games named below.
;; At runtime a particular game is represented as an integer in #{0 1 2}.

(def game-name ["Pool of Radiance" "Curse of the Azure Bonds" "Hillsfar"])
(def ngames (count game-name))
(def games (range ngames))

;; Three spiral paths are marked on the inner disk of the decoder wheel.
;; Words may be read off along these spirals once the inner disk has been
;; rotated to the proper position relative to the outer disk. The spirals
;; are numbered 0, 1 and 2.

(def nspirals 3)
(def spirals (range nspirals))

;; The edge of the outer disk holds "Espuar" (elvish) glyphs while the
;; outer edge of the inner disk contains "Dethek" (dwarvish) glyphs.
;; These two languages are numbered 0 and 1.

(def language-name ["Espuar" "Dethek"])
(def nlanguages 2)
(def espuar 0)
(def dethek 1)
(def languages [espuar dethek])

;; Each disk has 36 positions, of which 35 are for the glyphs of the
;; corresponding language. These are numbered 0 through 35.
;;
;; The final position on each ring is labeled "Translate from Dethek"
;; (on the Espuar ring) and "Translate from Espuar" (on the Dethek)
;; ring. We number this position -1. The glyph opposite this position
;; can be translated to a latin letter by reading off the first
;; character of the word associated with spiral 0.  This functionality
;; is not implemented here for lack of a nice way to present it on the
;; GUI.

(def nglyphs 35)
(def glyphs (range nglyphs))

;; The words that may be read off of the spirals on the inner disk
;; always are of length 6.

(def word-length 6)

;; There are 36 words since there are 36 positions on inner and outer
;; disks yielding 36 unique rotations of the disks relative to
;; eachother.

(def nwords-per-game 36)

(def words-for-game
     [[; Pool of Radiance
       "0SOMAS" "AXEIAX" "BEWARE" "COPPER" "DRAGON" "EFREET"
       "FRIEND" "GOOGLE" "HARASH" "IXYVSI" "JUNGLE" "KNIGHT"
       "LQLGMT" "MLSSXS" "NOTNOW" "OPTAWA" "POOLRD" "QUOHOG"
       "RHUDIA" "SAVIOR" "TEMPLE" "UICDRH" "VULCAN" "WYVERN"
       "XRSEHK" "YUFSTA" "ZOMBIE" "1GKKRY" "2IOLCD" "3MASAI"
       "4NINER" "5GUNGA" "6BROWN" "7GNATS" "8OASIS" "9TROUT"]
      [; Curse of the Azure Bonds
       "OMIMIC" "ATOMIE" "BEHOLD" "CLERIC" "DRUIDS" "ELVISH"
       "FUNGUS" "GIORGI" "HYDRAS" "INSIDE" "JAGUAR" "KEEPER" 
       "LOOTER" "MOGION" "NIXIES" "OTYUGH" "POWERS" "QUILLS"
       "RESIST" "SLIMES" "TROLLS" "URCHIN" "VERMIN" "WRAITH"
       "XERXES" "YULASH" "ZIRCON" "1MAGIC" "2ARROW" "3DRILL"
       "4PHLAN" "5POLAR" "6FIRST" "7AZURE" "8BONDS" "9ALIAS"]
      [; Hillsfar
       "0SAMAS" "ATTACK" "BOUNTY" "CUDGEL" "DRAGON" "EFREET"
       "FOREST" "GAMBLE" "HAGGLE" "IXYVSI" "JEWELS" "KNIGHT"
       "LQLGMT" "MYSTIC" "NECROS" "OPTAWA" "PRINCE" "QUESTS"
       "RHUDIA" "STEEDS" "TEMPLE" "UNLOCK" "VORPAL" "WYVERN" 
       "XRSEHK" "YUFSTA" "ZOMBIE" "1BLADE" "2AGILE" "3MASAI"
       "4MAGES" "5GUNGA" "6BROWN" "7GNATS" "8OASIS" "9TROUT"]])

(defn get-word
  "Given game #{0..2}, two glyphs #{-1..35} and a spiral #{0..2}
evaluate to the correct word. Any of the arguments may be nil, in
which case this function evaluates to nil."
  [game espuar-glyph dethek-glyph spiral]
  (when (and game espuar-glyph dethek-glyph spiral)
    (let [words (words-for-game game)
          word-nr (mod (+ 2 espuar-glyph dethek-glyph
                              (* spiral (/ nwords-per-game nspirals)))
                           nwords-per-game)]
      (words word-nr))))

;;; ======================================================================
;;; Model / Controller
;;; ======================================================================

(defprotocol Controller
  "Controller for single PoolradWheel window"
  (user-chose-glyph [this language n])
  (user-chose-spiral [this n])
  (user-chose-game [this n])
  (install-display-word-fn [this f]
   "(f word) is called when the user chooses a glyph, spiral or game."))

(defn model-controller
  "Return a new Controller (containing a model)."
  []
  (let[;; the model needs to remember four things: a game, two glyphs,
       ;; and a spiral. We just use a vector since we can just use
       ;; it via apply to supply the arguments to get-word.
       model (atom [nil nil nil nil])
       model! #(swap! model assoc %1 %2)
       display (atom println)
       display! #(swap! display (constantly %1))
       refresh #(@display (or (apply get-word @model) "      "))]
    (reify
     Controller
     (user-chose-glyph [_ lang glyph] (model! (inc lang) glyph) (refresh))
     (user-chose-spiral [_ spiral] (model! 3 spiral) (refresh))
     (user-chose-game [_ game] (model! 0 game) (refresh))
     (install-display-word-fn [_ f] (display! f) (refresh)))))

;;; ======================================================================
;;; View
;;; ======================================================================

;; ----------------------------------------------------------------------
;; Icon Handling
;; ----------------------------------------------------------------------

(def fmt-icon-name (partial format "poolradwheel/%d/%02d.png"))

(defn icon-name
  "Evaluate to the name of an image file on the classpath for the
given spiral or language and glyph."
  ([spiral] (fmt-icon-name 2 spiral))
  ([language glyph] (fmt-icon-name language (inc glyph))))

(defn load-icon
  [name]
  (-> name io/resource ImageIcon.))

(def ^{:doc "Provides an ImageIcon when given a spiral or a language
and a glyph."}
     get-icon (comp load-icon icon-name))

;; ----------------------------------------------------------------------
;; Swing Listener Utilities
;; ----------------------------------------------------------------------

;; We don't have to be overly general here. We're only interested in
;; ToggleButtons, which can be in either a selected or non-selected state.
;; Futhermore, we're only really interested when a toggle button becomes
;; selected as there can never be more than one selected button in a given
;; button group.

(defn item-listener
  "Given a function, f, return an ItemListener which calls f
with the selected state of the ItemEvent."
  [f]
  (letfn [(selected? [e] (= ItemEvent/SELECTED (.getStateChange e)))]
    (reify ItemListener
           (itemStateChanged
            [this e]
            (f (selected? e))))))

(defn when-selected
  "Register with component an ItemListener which will apply f to args
whenever the component is selected."
  [component f & args]
  (.addItemListener component (item-listener #(when % (apply f args)))))

;; ----------------------------------------------------------------------
;; Swing Containers and Layout
;; ----------------------------------------------------------------------

(defn box
  "Creates a box with :vertical or :horizontal orientation and populates
it with children. Children may be either JComponents or integers. Integers
are converted to vertical (or horizontal, respectively) struts."
  [orientation & children]
  (let [horizontal? (= orientation :horizontal)
        box (if horizontal?
              (Box/createHorizontalBox)
              (Box/createVerticalBox))
        add (if horizontal?
              #(.add box (if (integer? %) (Box/createHorizontalStrut %) %))
              #(.add box (if (integer? %) (Box/createVerticalStrut %) %)))]
    (dorun (map add children))
    box))

(def top-to-bottom (partial box :vertical))
(def left-to-right (partial box :horizontal))

(defn grid
  "Creates a JPanel with GridLayout with nrows by ncols. Components is
a sequence of JComponents which should have count nrows*ncols."
  [nrows ncols components]
  (let [panel (JPanel. (GridLayout. nrows ncols))
        add #(.add panel %)]
    (dorun (map add components))
    panel))

(defn make-toggle-buttons
  "Create a sequence of n ToggleButtons all belonging to the same
group. make-button-fn is called with each integer 0..n-1 and must
return a JToggleButton. The ToggleButtons returned will call
selected-fn with their integer when they are selected."
  [n make-button-fn selected-fn]
  (let [group (ButtonGroup.)
        add-to-group #(.add group %)]
    (for [x (range n)]
      (doto (make-button-fn x)
        (when-selected selected-fn x)
        (add-to-group)))))

(defn make-toggle-button-panel
  "Make a JPanel of nrows rows of ncols columns of JToggleButtons in a
GridLayout."
  [nrows ncols make-button-fn selected-fn]
  (grid nrows ncols
        (make-toggle-buttons (* nrows ncols) make-button-fn selected-fn)))

(defn make-label
  "Create a JLabel displaying the given text. The layout of our GUI
looks nicer if we embed it in a JPanel, so we do."
  [text]
  (doto (JPanel.)
    (.add (JLabel. text))))

;; ----------------------------------------------------------------------
;; Application-Specific Swing GUI Creation Functions
;; ----------------------------------------------------------------------

(defn make-game-panel
  "Creates a panel of radio buttons allowing the user to choose one of
the three supported games."
  [controller]
  (let [game-button #(JRadioButton. (game-name %1))
        select-game (partial user-chose-game controller)]
    (top-to-bottom
     (make-label "Game")
     (make-toggle-button-panel 3 1 game-button select-game))))

(defn make-spiral-panel
  "Creates a panel of toggle buttons allowing the user to choose one
of the three possible spirals."
  [controller]
  (let [spiral-button #(JToggleButton. (get-icon %1))
        select-spiral (partial user-chose-spiral controller)]
    (top-to-bottom
     (make-label "Spiral")
     (make-toggle-button-panel 1 3 spiral-button select-spiral))))

(defn make-glyph-panel
  "Create a panel allowing the user to choose one of the 35 glyphs of
the given language."
  [controller language]
  (let [glyph-button #(JToggleButton. (get-icon language %1))
        select-glyph (partial user-chose-glyph controller language)]
    (top-to-bottom
     (make-label (language-name language))
     (make-toggle-button-panel 7 5 glyph-button select-glyph))))

(defn make-output-panel
  "Create a panel for displaying the word decoded from the currently
selected game, glyphs and spiral. We install a display-word function
in controller to accomplish this."
  [controller]
  (let [labels (for [x (range word-length)]
                 (doto (JLabel. " ")
                   (.setHorizontalAlignment SwingConstants/CENTER)))
        set-text (fn [label char] (.setText label (str char)))
        display-word (fn [word] (dorun (map set-text labels word)))]
    (install-display-word-fn controller display-word)
    (top-to-bottom
     (make-label "Output Word")
     (grid 1 6 labels))))

(defn make-main-window-content
  "Creates the content of the Main (and only) Window."
  [controller]
  (let [strut 6]
    (top-to-bottom
     (left-to-right
      (make-game-panel controller)
      (make-spiral-panel controller))
     strut
     (left-to-right
      (make-glyph-panel controller espuar)
      (* 2 strut)
      (make-glyph-panel controller dethek))
     strut
     (make-output-panel controller))))

(defn open-window
  "Creates a new top-level window (JFrame) with the given
Content. on-close, if provided must be one of the constants
JFrame/HIDE_ON_CLOSE, JFrame/DISPOSE_ON_CLOSE,
JFrame/EXIT_ON_CLOSE. The default is HIDE_ON_CLOSE."
    ([title on-close content]
     (doto (JFrame. title)
       (.setContentPane content)
       (.pack)
       (.setVisible true)
       (.setDefaultCloseOperation on-close)))
  ([title content]
     (open-window title JFrame/HIDE_ON_CLOSE content)))

(defn open-main-window
  "Open main Window. Program exits when this window is closed."
  []
  (open-window "Wheel" JFrame/EXIT_ON_CLOSE
               (make-main-window-content (model-controller))))

(def -main open-main-window)