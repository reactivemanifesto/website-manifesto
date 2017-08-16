package models

import java.util.Locale
import scala.collection.SortedMap

object Trie {
  def apply[A](values: (String, A)*) = empty ++ values
  def empty[A]: Trie[A] = new Trie[A]()
}

/**
 * An immutable, case insensitive, multi value String based index that allows lookups by prefix
 */
class Trie[A](nodes: SortedMap[Char, Trie[A]] = SortedMap.empty[Char, Trie[A]], values: Set[A] = Set.empty[A]) {

  /**
   * Get all the values that match the given prefix.
   */
  def getAllWithPrefix(prefix: String): List[A] = nodeFor(normalise(prefix)).toList.flatMap(_.getAll)

  /**
   * Add the given key value pair to the index.
   */
  def +(keyValue: (String, A)): Trie[A] = put(normalise(keyValue._1), keyValue._2)

  /**
   * Add the given key value pairs to the index
   */
  def ++(keyValues: TraversableOnce[(String, A)]): Trie[A] = keyValues.foldLeft(this)(_ + _)

  /**
   * Index the given value with the given keys
   */
  def index(keys: TraversableOnce[String], value: A) = this ++ keys.map(_ -> value)

  /**
   * Remove the given value at the given key.
   */
  def -(keyValue: (String, A)): Trie[A] = remove(normalise(keyValue._1), _ == keyValue._2)

  /**
   * Filter the values at the given key with the given predicate
   */
  def deindex(keys: TraversableOnce[String], predicate: A => Boolean): Trie[A] =
    keys.foldLeft(this)((trie, key) => remove(normalise(key), predicate))

  /**
   * Whether this index is empty.
   */
  def isEmpty = values.isEmpty && nodes.isEmpty

  private def normalise(s: String) = s.trim().toLowerCase(Locale.ROOT).toList

  private def remove(key: List[Char], predicate: A => Boolean): Trie[A] = key match {
    case Nil => new Trie(nodes, values.filterNot(predicate))
    case c :: rest =>
      nodes.get(c).map(_.remove(rest, predicate)).filterNot(_.isEmpty) match {
        case Some(node) => new Trie(nodes + (c -> node), values)
        case None => new Trie(nodes - c, values)
      }
  }

  private def put(key: List[Char], value: A): Trie[A] = key match {
    case Nil => new Trie(nodes, values + value)
    case c :: rest =>
      val node = nodes.get(c).getOrElse(new Trie()).put(rest, value)
      new Trie(nodes + (c -> node), values)
  }
  
  private def getAll: Set[A] = nodes.values.toSet.flatMap((trie: Trie[A]) => trie.getAll) ++ values

  private def nodeFor(prefix: List[Char]): Option[Trie[A]] = prefix match {
    case Nil => Some(this)
    case c :: rest => for {
      child <- nodes.get(c)
      node <- child.nodeFor(rest)
    } yield node
  }
}
