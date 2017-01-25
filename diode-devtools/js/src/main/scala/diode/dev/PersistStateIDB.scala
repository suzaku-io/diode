package diode.dev

import org.scalajs.dom
import org.scalajs.dom.raw.{Event, IDBDatabase, IDBObjectStore}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.scalajs.js

class PersistStateIDB[M <: AnyRef, P <: js.Any](pickleF: M => P, unpickleF: P => M) extends PersistState[M, P] {
  final val storeName = "snapshots"

  // Opens (and creates) a database for snapshots
  // Contains the DB in a future, for easy async access
  private lazy val dbF = {
    if (js.isUndefined(dom.window.indexedDB)) {
      Future.failed(new Exception("IndexedDB is not supported"))
    } else {
      val req = dom.window.indexedDB.open("DiodePersistence")
      val p   = Promise[IDBDatabase]
      req.onsuccess = (_: Event) => {
        println("Open onsuccess")
        p.success(req.result.asInstanceOf[IDBDatabase])
      }
      req.onupgradeneeded = (_: Event) => {
        println("Open onupgrade")
        val db = req.result.asInstanceOf[IDBDatabase]
        db.createObjectStore(storeName)
      }
      req.onerror = (_: Event) => {
        println(s"Open onerror ${req.error.name}")
        p.failure(new Exception(s"IDB error ${req.error.name}"))
      }
      p.future
    }
  }

  private def withStore[A](f: IDBObjectStore => Future[A]) = {
    dbF.flatMap { db =>
      val tx    = db.transaction(storeName, "readwrite")
      val store = tx.objectStore(storeName)
      f(store)
    }
  }

  override def pickle(model: M): P = pickleF(model)

  override def unpickle(pickled: P): M = unpickleF(pickled)

  override def save(id: String, pickled: P): Unit = {
    withStore { store =>
      val p   = Promise[String]
      val req = store.put(pickled, id)
      req.onsuccess = (_: Event) => {
        println("Save onsuccess")
        p.success(req.result.asInstanceOf[String])
      }
      req.onerror = (_: Event) => {
        println(s"Save onerror ${req.error.name}")
        p.failure(new Exception(s"IDB error ${req.error.name}"))
      }
      p.future
    }
  }

  override def load(id: String): Future[P] = {
    withStore { store =>
      val p   = Promise[P]
      val req = store.get(id)
      req.onsuccess = (_: Event) => {
        println("Load onsuccess")
        p.success(req.result.asInstanceOf[P])
      }
      req.onerror = (_: Event) => {
        println(s"Load onerror ${req.error.name}")
        p.failure(new Exception(s"IDB error ${req.error.name}"))
      }
      p.future
    }
  }
}
