package HeraclesStreaming

case class ErrorMsg(error_id: Int, error_msg: String, error_time:Long) {
  override def toString: String = {
    s"$error_id::$error_msg::$error_time"
  }
}