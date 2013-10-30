package models

import org.specs2.mutable.Specification

class TrieSpec extends Specification {

  "Trie" should {
    "lookup values by prefix" in {
      Trie("foo" -> "a", "food" -> "b", "fo" -> "c", "fod" -> "d").getAllWithPrefix("foo") must contain("a", "b").only
    }
    "allow storing multiple values at one key" in {
      Trie("foo" -> "a", "foo" -> "b").getAllWithPrefix("foo") must contain("a", "b").only
    }
    "only return one value if multiple found at the same prefix" in {
      Trie("foo" -> "a", "food" -> "a").getAllWithPrefix("foo") must_== Seq("a")
    }
    "allow adding elements" in {
      val trie = Trie("foo" -> "a") + ("bar" -> "b")
      trie.getAllWithPrefix("foo") must_== Seq("a")
      trie.getAllWithPrefix("bar") must_== Seq("b")
      trie.getAllWithPrefix("") must contain("a", "b").only
    }
    "allow adding elements to an existing key" in {
      val trie = Trie("foo" -> "a") + ("foo" -> "b")
      trie.getAllWithPrefix("foo") must contain("a", "b").only
    }
    "allow adding elements to a child key" in {
      val trie = Trie("foo" -> "a") + ("food" -> "b")
      trie.getAllWithPrefix("foo") must contain("a", "b").only
      trie.getAllWithPrefix("food") must_== Seq("b")
    }
    "allow adding elements to a parent key" in {
      val trie = Trie("foo" -> "a") + ("fo" -> "b")
      trie.getAllWithPrefix("fo") must contain("a", "b").only
      trie.getAllWithPrefix("foo") must_== Seq("a")
    }
    "allow removing elements" in {
      val trie = Trie("foo" -> "a", "bar" -> "b") - ("bar" -> "b")
      trie.getAllWithPrefix("") must_== Seq("a")
    }
    "not remove elements if the value isn't equal" in {
      val trie = Trie("foo" -> "a", "bar" -> "b") - ("bar" -> "c")
      trie.getAllWithPrefix("") must contain("a", "b").only
    }
    "not remove elements if the key is a child" in {
      val trie = Trie("foo" -> "a", "bar" -> "b") - ("bare" -> "b")
      trie.getAllWithPrefix("") must contain("a", "b").only
    }
    "not remove elements if the key is a parent" in {
      val trie = Trie("foo" -> "a", "bar" -> "b") - ("ba" -> "b")
      trie.getAllWithPrefix("") must contain("a", "b").only
    }
    "be empty after removing all element" in {
      val trie = Trie("foo" -> "a", "food" -> "b", "fo" -> "c", "fod" -> "d") -
        ("foo" -> "a") - ("food" -> "b") - ("fo" -> "c") - ("fod" -> "d")
      trie.isEmpty must beTrue
    }
    "safely remove elements from child nodes" in {
      val trie = Trie("foo" -> "a", "food" -> "b") - ("food" -> "b")
      trie.getAllWithPrefix("") must_== Seq("a")
    }
    "safely remove elements from parent nodes" in {
      val trie = Trie("foo" -> "a", "food" -> "b") - ("foo" -> "a")
      trie.getAllWithPrefix("") must_== Seq("b")
    }
    "allow removing elements from the same node" in {
      val trie = Trie("foo" -> "a", "foo" -> "b") - ("foo" -> "b")
      trie.getAllWithPrefix("") must_== Seq("a")
    }

  }

}
