import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SearchBar } from './SearchBar'

describe('SearchBar', () => {
  it('renders a controlled value and emits changes through onChange', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()

    render(<SearchBar value="harina" onChange={handleChange} />)

    const input = screen.getByLabelText('Buscar productos')
    expect(input).toHaveValue('harina')

    await user.type(input, 'x')

    expect(handleChange).toHaveBeenCalled()
    expect(handleChange).toHaveBeenLastCalledWith('harinax')
  })
})
