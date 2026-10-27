import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useGrades, useRebatePolicies, useRegisterRebatePolicy } from '../../api/master'
import DataTable, { type Column } from '../../components/DataTable'
import StatusBadge from '../../components/StatusBadge'
import { POLICY_STATES, labelOf } from '../../lib/codes'
import { addDays, formatDate, formatRate, today } from '../../lib/format'
import type { Grade, RebatePolicy } from '../../types/master'

interface FormValues {
  gradeCode: string
  ratePercent: number
  validFrom: string
}

/** valid_to는 미포함 날짜라서 화면에는 하루 전 날짜를 "까지"로 보여 준다 */
function period(p: RebatePolicy) {
  return `${formatDate(p.validFrom)} ~ ${p.validTo ? addDays(p.validTo, -1) : ''}`
}

export default function RebatePoliciesPage() {
  const [gradeFilter, setGradeFilter] = useState('')
  const { data: grades, isPending: gradesPending } = useGrades()
  const { data: policies, isPending: policiesPending } = useRebatePolicies(gradeFilter)
  const registerPolicy = useRegisterRebatePolicy()
  const tomorrow = addDays(today(), 1)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ defaultValues: { gradeCode: '', validFrom: tomorrow } })

  const onSubmit = handleSubmit((form) => {
    registerPolicy.mutate(
      {
        gradeCode: form.gradeCode,
        // 2.5(%) → 0.025. 부동소수점 오차를 없애려고 소수 넷째 자리로 맞춘다
        rebateRate: Number((form.ratePercent / 100).toFixed(4)),
        validFrom: form.validFrom,
      },
      { onSuccess: () => reset({ gradeCode: '', validFrom: tomorrow }) },
    )
  })

  const gradeColumns: Column<Grade>[] = [
    { key: 'name', header: '등급', render: (g) => g.name },
    { key: 'current', header: '현재 요율', render: (g) => formatRate(g.currentRate), align: 'right' },
    {
      key: 'next',
      header: '변경 예정',
      render: (g) => (g.nextRate == null ? '-' : `${formatDate(g.nextValidFrom)}부터 ${formatRate(g.nextRate)}`),
      align: 'right',
    },
  ]

  const policyColumns: Column<RebatePolicy>[] = [
    { key: 'grade', header: '등급', render: (p) => p.gradeName, width: '100px' },
    { key: 'rate', header: '요율', render: (p) => formatRate(p.rebateRate), align: 'right', width: '90px' },
    { key: 'period', header: '적용 기간', render: period },
    {
      key: 'state',
      header: '상태',
      render: (p) => <StatusBadge value={p.state} label={labelOf(POLICY_STATES, p.state)} />,
      width: '90px',
    },
  ]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">리베이트 정책</h1>
      </div>
      <p className="page-description">
        거래처 등급별 리베이트율입니다. 정산할 때는 주문의 출고일에 적용 중이던 요율을 씁니다.
      </p>

      <div className="split">
        <div>
          <h2 className="section-title">등급별 요율</h2>
          <DataTable
            caption="등급별 현재 요율"
            columns={gradeColumns}
            rows={grades}
            rowKey={(g) => g.gradeCode}
            loading={gradesPending}
            emptyMessage="등록된 등급이 없습니다."
          />
        </div>

        <form className="panel form" onSubmit={onSubmit} noValidate>
          <h2 className="section-title">요율 변경 등록</h2>
          <div className="field">
            <label htmlFor="gradeCode">등급</label>
            <select id="gradeCode" {...register('gradeCode', { required: '등급을 선택해 주세요.' })}>
              <option value="">선택</option>
              {grades?.map((g) => (
                <option key={g.gradeCode} value={g.gradeCode}>{g.name}</option>
              ))}
            </select>
            {errors.gradeCode && <p className="field-error">{errors.gradeCode.message}</p>}
          </div>
          <div className="field">
            <label htmlFor="ratePercent">새 요율 (%)</label>
            <input
              id="ratePercent"
              type="number"
              step={0.01}
              min={0}
              max={50}
              {...register('ratePercent', {
                valueAsNumber: true,
                validate: (v) =>
                  (Number.isFinite(v) && v >= 0 && v <= 50) || '0에서 50 사이 값을 입력해 주세요.',
              })}
            />
            {errors.ratePercent && <p className="field-error">{errors.ratePercent.message}</p>}
          </div>
          <div className="field">
            <label htmlFor="validFrom">적용 시작일</label>
            <input
              id="validFrom"
              type="date"
              min={tomorrow}
              {...register('validFrom', {
                required: '적용 시작일을 입력해 주세요.',
                validate: (v) => v >= tomorrow || '내일 이후 날짜만 지정할 수 있습니다.',
              })}
            />
            {errors.validFrom ? (
              <p className="field-error">{errors.validFrom.message}</p>
            ) : (
              <p className="field-hint">기존 요율은 시작일 전날까지 적용됩니다.</p>
            )}
          </div>
          {registerPolicy.isError && <p className="form-error" role="alert">{registerPolicy.error.message}</p>}
          {registerPolicy.isSuccess && <p className="form-success" role="status">요율 변경을 등록했습니다.</p>}
          <button type="submit" className="button button-primary" disabled={registerPolicy.isPending}>
            요율 변경 등록
          </button>
        </form>
      </div>

      <div className="section-header">
        <h2 className="section-title">변경 이력</h2>
        <select value={gradeFilter} onChange={(e) => setGradeFilter(e.target.value)} aria-label="등급으로 거르기">
          <option value="">전체 등급</option>
          {grades?.map((g) => (
            <option key={g.gradeCode} value={g.gradeCode}>{g.name}</option>
          ))}
        </select>
      </div>
      <DataTable
        caption="리베이트 정책 변경 이력"
        columns={policyColumns}
        rows={policies}
        rowKey={(p) => p.id}
        loading={policiesPending}
        emptyMessage="등록된 정책이 없습니다."
      />
    </section>
  )
}
