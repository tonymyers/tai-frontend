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

package controllers.income.estimatedPay.update

import controllers.TaiBaseController
import controllers.actions.ValidatePerson
import controllers.auth.AuthAction
import javax.inject.{Inject, Named}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.tai.cacheResolver.estimatedPay.UpdatedEstimatedPayJourneyCache
import uk.gov.hmrc.tai.forms.PayPeriodForm
import uk.gov.hmrc.tai.model.domain.income.IncomeSource
import uk.gov.hmrc.tai.service.journeyCache.JourneyCacheService
import uk.gov.hmrc.tai.util.constants.JourneyCacheConstants
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import uk.gov.hmrc.play.partials.FormPartialRetriever
import uk.gov.hmrc.renderer.TemplateRenderer

class IncomeUpdatePayPeriodController @Inject()(
  authenticate: AuthAction,
  validatePerson: ValidatePerson,
  @Named("Update Income") implicit val journeyCacheService: JourneyCacheService,
  override implicit val partialRetriever: FormPartialRetriever,
  override implicit val templateRenderer: TemplateRenderer)
    extends TaiBaseController with JourneyCacheConstants with UpdatedEstimatedPayJourneyCache {

  def payPeriodPage: Action[AnyContent] = (authenticate andThen validatePerson).async { implicit request =>
    implicit val user = request.taiUser

    val employerFuture = IncomeSource.create(journeyCacheService)

    for {
      employer        <- employerFuture
      payPeriod       <- journeyCacheService.currentValue(UpdateIncome_PayPeriodKey)
      payPeriodInDays <- journeyCacheService.currentValue(UpdateIncome_OtherInDaysKey)
    } yield {
      val form: Form[PayPeriodForm] = PayPeriodForm.createForm(None).fill(PayPeriodForm(payPeriod, payPeriodInDays))
      Ok(views.html.incomes.payPeriod(form, employer.id, employer.name))
    }
  }

  def handlePayPeriod: Action[AnyContent] = (authenticate andThen validatePerson).async { implicit request =>
    implicit val user = request.taiUser

    val payPeriod: Option[String] = request.body.asFormUrlEncoded.flatMap(m => m.get("payPeriod").flatMap(_.headOption))

    PayPeriodForm
      .createForm(None, payPeriod)
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val employerFuture = IncomeSource.create(journeyCacheService)
          for {
            employer <- employerFuture
          } yield {
            val isDaysError = formWithErrors.errors.exists { error =>
              error.key == PayPeriodForm.OTHER_IN_DAYS_KEY
            }
            BadRequest(views.html.incomes.payPeriod(formWithErrors, employer.id, employer.name, !isDaysError))
          }
        },
        formData => {
          val cacheMap = formData.otherInDays match {
            case Some(days) =>
              Map(
                UpdateIncome_PayPeriodKey   -> formData.payPeriod.getOrElse(""),
                UpdateIncome_OtherInDaysKey -> days.toString)
            case _ => Map(UpdateIncome_PayPeriodKey -> formData.payPeriod.getOrElse(""))
          }

          journeyCache(cacheMap = cacheMap) map { _ =>
            Redirect(routes.IncomeUpdatePayslipAmountController.payslipAmountPage())
          }
        }
      )
  }

}
