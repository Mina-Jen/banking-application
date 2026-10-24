import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PasswordInput } from './PasswordInput'

function renderInForm() {
  render(
    <form onSubmit={(event) => event.preventDefault()}>
      <PasswordInput label="Password" name="password" defaultValue="s3cret-pass" />
      <button type="submit">Save</button>
    </form>,
  )
  return {
    field: screen.getByLabelText('Password') as HTMLInputElement,
    toggle: screen.getByRole('button', { name: 'Show password' }),
  }
}

describe('PasswordInput', () => {
  it('starts hidden', () => {
    const { field } = renderInForm()
    expect(field.type).toBe('password')
  })

  it('reveals and hides the password, renaming the toggle each time', async () => {
    const user = userEvent.setup()
    const { field, toggle } = renderInForm()

    await user.click(toggle)
    expect(field.type).toBe('text')
    expect(toggle).toHaveAccessibleName('Hide password')
    expect(toggle).toHaveTextContent(/^Hide/)

    await user.click(toggle)
    expect(field.type).toBe('password')
    expect(toggle).toHaveAccessibleName('Show password')
  })

  it('points the toggle at the field it controls', () => {
    const { field, toggle } = renderInForm()
    expect(toggle).toHaveAttribute('aria-controls', field.id)
  })

  // Playwright looks the field up with getByLabel('Password', { exact: true });
  // an aria-label on the toggle would make that ambiguous.
  it('leaves the field as the only control labelled exactly "Password"', () => {
    renderInForm()
    expect(screen.getAllByLabelText('Password')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Show password' })).not.toHaveAttribute('aria-label')
  })

  it('is a button that does not submit the form', () => {
    const { toggle } = renderInForm()
    expect(toggle).toHaveAttribute('type', 'button')
  })

  it('hides the password again when the form is submitted', async () => {
    const user = userEvent.setup()
    const { field, toggle } = renderInForm()

    await user.click(toggle)
    expect(field.type).toBe('text')

    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(field.type).toBe('password')
  })

  it('keeps the field reachable by keyboard before its toggle', async () => {
    const user = userEvent.setup()
    const { field, toggle } = renderInForm()

    await user.tab()
    expect(field).toHaveFocus()
    await user.tab()
    expect(toggle).toHaveFocus()
  })

  it('wires an error to the field', () => {
    render(<PasswordInput label="Password" invalid hint="Choose a password." />)
    const field = screen.getByLabelText('Password')
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field).toHaveAccessibleDescription('Choose a password.')
    expect(screen.getByRole('alert')).toHaveTextContent('Choose a password.')
  })
})
