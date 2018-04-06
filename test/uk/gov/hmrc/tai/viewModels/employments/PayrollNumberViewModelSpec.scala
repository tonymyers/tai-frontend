/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.viewModels.employments

import controllers.FakeTaiPlayApplication
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.util.{FormValuesConstants, JourneyCacheConstants}
import play.api.i18n.Messages.Implicits._

class PayrollNumberViewModelSpec extends PlaySpec  with JourneyCacheConstants with FormValuesConstants with FakeTaiPlayApplication {

  "Payroll number view model" must {
    "create an instance of view model" when {

      "employment name and firstPayChoice is yes" in {
        val cacheMap = Map(AddEmployment_NameKey -> "XJ", AddEmployment_StartDateWithinSixWeeks -> YesValue)
        val result = PayrollNumberViewModel(cacheMap)
        result mustBe PayrollNumberViewModel("XJ", true)

      }

      "employment name and firstPayChoice is no" in {
        val cacheMap = Map(AddEmployment_NameKey -> "XJ")
        val result = PayrollNumberViewModel(cacheMap)
        result mustBe PayrollNumberViewModel("XJ", false)
      }
    }
  }

}
