package HeraclesFeeder

case class ErrorMsg(error_id: Int, error_msg: String, error_time:Long) {
  override def toString: String = {
    s"$error_id::$error_msg::$error_time"
  }
}

case class UserLogin(user_id:Int, login_success:Boolean, error_id:Int, day_offset:Int) {
  override def toString: String = {
    s"$user_id::$login_success::$error_id::$day_offset"
  }
}