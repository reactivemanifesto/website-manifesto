package controllers

import play.api.mvc._
import play.api.libs.iteratee.{Iteratee, Enumerator, Done}
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Action for handling HEAD requests made to GET actions
 */
class HeadAction(wrapped: EssentialAction) extends EssentialAction with RequestTaggingHandler {

  def apply(req: RequestHeader) = {

    def skipBody(result: Result): Result = result match {
      case SimpleResult(header, body) => {
        // Tell the body enumerator it's done so that it can clean up resources
        body(Done(()))
        SimpleResult(header, Enumerator(Results.EmptyContent()))
      }
      case ChunkedResult(header, body) => {
        body(Done(()))
        ChunkedResult(header, (it: Iteratee[Array[Byte], Unit]) => it.run)
      }
      case AsyncResult(future) => AsyncResult(future.map(skipBody))
    }

    // Invoke the wrapped action
    wrapped(req).map { result =>
      skipBody(result)
    }
  }

  // Ensure that request tags are added if necessary
  def tagRequest(request: RequestHeader) = wrapped match {
    case tagging: RequestTaggingHandler => tagging.tagRequest(request)
    case _ => request
  }
}
