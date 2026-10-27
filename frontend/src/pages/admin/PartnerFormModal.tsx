import { useForm } from 'react-hook-form'
import { useGrades, useSavePartner } from '../../api/master'
import { PARTNER_STATUSES } from '../../lib/codes'
import type { Partner, PartnerForm } from '../../types/master'

interface Props {
  partner: Partner | null
  onDone: () => void
}

export default function PartnerFormModal({ partner, onDone }: Props) {
  const save = useSavePartner()
  const { data: grades } = useGrades()
  const editing = partner !== null

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PartnerForm>({
    defaultValues: partner
      ? { code: partner.code, name: partner.name, gradeCode: partner.gradeCode, status: partner.status }
      : { code: '', name: '', gradeCode: '', status: 'ACTIVE' },
  })

  const onSubmit = handleSubmit((form) => {
    save.mutate({ id: partner?.id, form: { ...form, code: form.code.trim().toUpperCase() } }, { onSuccess: onDone })
  })

  return (
    <form className="form" onSubmit={onSubmit} noValidate>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="code">거래처 코드</label>
          <input
            id="code"
            disabled={editing}
            placeholder="P0004"
            {...register('code', {
              required: '거래처 코드를 입력해 주세요.',
              pattern: { value: /^[Pp]\d{4}$/, message: 'P와 숫자 4자리로 입력해 주세요.' },
            })}
          />
          {errors.code && <p className="field-error">{errors.code.message}</p>}
        </div>

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

        <div className="field field-wide">
          <label htmlFor="name">거래처명</label>
          <input id="name" {...register('name', { required: '거래처명을 입력해 주세요.' })} />
          {errors.name && <p className="field-error">{errors.name.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="status">거래 상태</label>
          <select id="status" {...register('status')}>
            {PARTNER_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
        </div>
      </div>

      {editing && (
        <p className="field-hint">
          등급을 바꿔도 이미 접수된 주문은 주문 당시 등급으로 정산됩니다. 거래를 중지하면 소속 사용자는 로그인할 수 없습니다.
        </p>
      )}
      {save.isError && <p className="form-error" role="alert">{save.error.message}</p>}

      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={onDone}>
          닫기
        </button>
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          {editing ? '변경 내용 저장' : '거래처 등록'}
        </button>
      </div>
    </form>
  )
}
