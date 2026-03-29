# Stripe test card numbers

Use these **only** with Stripe **test** keys (`sk_test_...`, `pk_test_...`). They do not process real payments.

- **Expiry:** Any future date (e.g. `12/34`)
- **CVC:** Any 3 digits (e.g. `123`)
- **Postal code:** Any (e.g. `12345`)

| Scenario              | Card number             |
|-----------------------|-------------------------|
| **Success**           | `4242 4242 4242 4242`  |
| **Requires 3D Secure**| `4000 0025 0000 3155`   |
| **Declined (insufficient funds)** | `4000 0000 0000 9995` |
| **Declined (generic)**| `4000 0000 0000 0002`   |

More: [Stripe Testing – Card numbers](https://docs.stripe.com/testing#cards)
