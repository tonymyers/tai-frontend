/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.tai.util

import org.joda.time.LocalDate
import play.api.i18n.Messages
import uk.gov.hmrc.play.language.LanguageUtils.Dates

object DateHelper {

  def toDisplayFormat(date: Option[LocalDate])(implicit messages: Messages): String =
    date match {
      case Some(dt) => Dates.formatDate(dt)
      case _        => ""
    }

  implicit val dateTimeOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)

  def mostRecentDate(dates: Seq[LocalDate]): LocalDate =
    dates.min

  def monthOfYear(date: String): String = {
    val monthRegex = "[A-Za-z]+".r
    monthRegex.findFirstIn(date).getOrElse("")
  }

  def getMonthAndYear(date: String): String = {
    val monthAndYearRegex = "[A-Za-z].*".r
    monthAndYearRegex.findFirstIn(date).getOrElse("")
  }

}
