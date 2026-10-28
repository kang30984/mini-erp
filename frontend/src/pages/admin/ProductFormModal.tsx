import { useForm } from 'react-hook-form'
import { useSaveProduct } from '../../api/master'
import { CATEGORIES, PRODUCT_STATUSES } from '../../lib/codes'
import type { Product, ProductForm } from '../../types/master'

interface Props {
  product: Product | null
  onDone: () => void
}

export default function ProductFormModal({ product, onDone }: Props) {
  const save = useSaveProduct()
  const editing = product !== null

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ProductForm>({
    defaultValues: product
      ? {
          sku: product.sku,
          name: product.name,
          category: product.category,
          unitPrice: product.unitPrice,
          stockQty: product.stockQty,
          status: product.status,
        }
      : { sku: '', name: '', category: '', status: 'ON_SALE' },
  })

  const onSubmit = handleSubmit((form) => {
    // 수정할 때는 불러왔을 때의 재고를 같이 보낸다. 그 사이 승인 등으로 재고가 바뀌었으면 서버가 409로 거절한다
    const payload: ProductForm = { ...form, sku: form.sku.trim().toUpperCase() }
    if (product) payload.baseStockQty = product.stockQty
    save.mutate({ id: product?.id, form: payload }, { onSuccess: onDone })
  })

  return (
    <form className="form" onSubmit={onSubmit} noValidate>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="sku">상품 코드</label>
          <input
            id="sku"
            disabled={editing}
            aria-invalid={errors.sku ? 'true' : 'false'}
            {...register('sku', {
              required: '상품 코드를 입력해 주세요.',
              pattern: { value: /^[A-Za-z0-9-]+$/, message: '영문, 숫자, 하이픈만 쓸 수 있습니다.' },
            })}
          />
          {editing ? (
            <p className="field-hint">주문 이력과 연결된 코드라 수정할 수 없습니다.</p>
          ) : (
            errors.sku && <p className="field-error">{errors.sku.message}</p>
          )}
        </div>

        <div className="field">
          <label htmlFor="category">분류</label>
          <select id="category" {...register('category', { required: '분류를 선택해 주세요.' })}>
            <option value="">선택</option>
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>{c.label}</option>
            ))}
          </select>
          {errors.category && <p className="field-error">{errors.category.message}</p>}
        </div>

        <div className="field field-wide">
          <label htmlFor="name">상품명</label>
          <input id="name" {...register('name', { required: '상품명을 입력해 주세요.' })} />
          {errors.name && <p className="field-error">{errors.name.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="unitPrice">단가 (원)</label>
          <input
            id="unitPrice"
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            {...register('unitPrice', {
              valueAsNumber: true,
              validate: (v) => (Number.isInteger(v) && v >= 0) || '0 이상의 정수로 입력해 주세요.',
            })}
          />
          {errors.unitPrice && <p className="field-error">{errors.unitPrice.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="stockQty">재고 수량</label>
          <input
            id="stockQty"
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            {...register('stockQty', {
              valueAsNumber: true,
              validate: (v) => (Number.isInteger(v) && v >= 0) || '0 이상의 정수로 입력해 주세요.',
            })}
          />
          {errors.stockQty && <p className="field-error">{errors.stockQty.message}</p>}
        </div>

        <div className="field">
          <label htmlFor="status">판매 상태</label>
          <select id="status" {...register('status')}>
            {PRODUCT_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
        </div>
      </div>

      {save.isError && <p className="form-error" role="alert">{save.error.message}</p>}

      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={onDone}>
          닫기
        </button>
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          {editing ? '변경 내용 저장' : '상품 등록'}
        </button>
      </div>
    </form>
  )
}
