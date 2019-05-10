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

package views.html

import controllers.FakeTaiPlayApplication
import mocks.{MockPartialRetriever, MockTemplateRenderer}
import org.mockito.Mockito.when
import play.api.test.{FakeApplication, FakeRequest}
import play.twirl.api.Html
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.util.viewHelpers.TaiViewSpec

class mainTemplateSpec extends TaiViewSpec with FakeTaiPlayApplication {

  implicit val testTemplateRenderer = MockTemplateRenderer
  implicit val testPartialRetriever = MockPartialRetriever
  override lazy val fakeApplication = FakeApplication()
  override def view = views.html.main(""){Html("")}(FakeRequest(), messages, testTemplateRenderer,testPartialRetriever)

  "main template" must {

    "include webchat script" when {

      "webchat is toggled on" in {
        when(FeatureTogglesConfig.webChatEnabled).thenReturn(true)
        doc must haveElementWithId("#webchat-tag")
      }

    }


//    "not include webchat script" when {
//
//      "webchat is toggled on but include nuance webchat is false" in {
//
//
//        doc mustNot haveElementWithId("header")
//
//      }
//
//    }
  }

}
