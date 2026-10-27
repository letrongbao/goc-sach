"""HTTP acceptance on a disposable Compose demo; never invokes real payment/upload."""
import argparse
import http.cookiejar
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Client:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.http = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
        self.token = None

    def call(self, path, method='GET', data=None, form=False, expected=200):
        headers = {}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        if data is not None:
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
            data = (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            response = self.http.open(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status, body = response.code, response.read().decode('utf-8')
        assert status == expected, f'{method} {path}: expected {expected}, got {status}'
        return body

    def api(self, path, method='GET', data=None, expected=200):
        body = self.call(path, method, data, expected=expected)
        return json.loads(body) if body else None

    def csrf(self):
        body = self.call('/auth/login')
        found = re.search(r'name="_csrf" value="([^"]+)"', body)
        assert found, 'CSRF field missing'
        return found.group(1)

    def login(self, username):
        self.call('/auth/login', 'POST', {'_csrf': self.csrf(), 'login': username,
                  'password': 'demo-password-123', 'mode': 'session'}, form=True, expected=302)
        result = self.call('/auth/api-token', 'POST', {'_csrf': self.csrf()}, form=True)
        self.token = json.loads(result)['access_token']
        assert self.api('/api/me')['username'] == username


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:8080')
    parser.add_argument('--mail', default='http://127.0.0.1:8025')
    parser.add_argument('--verify-existing', type=Path)
    parser.add_argument('--result', type=Path, default=Path('target/compose-check.json'))
    args = parser.parse_args()
    buyer = Client(args.base)
    if args.verify_existing:
        saved = json.loads(args.verify_existing.read_text(encoding='utf-8'))
        buyer.login(saved['username'])
        assert buyer.api('/api/account/profile')['displayName'] == 'Compose Acceptance'
        assert buyer.api(f"/api/orders/{saved['orderId']}")['order']['status'] == 'COMPLETED'
        assert buyer.api(f"/api/orders/{saved['orderId']}/return")['status'] == 'REFUNDED'
        print('PASS: account, profile, order and refund persisted after restart/recreate.')
        return

    for path in ['/api/categories', '/api/products', '/api/products/1', '/api/shops', '/api/rankings/bestselling']:
        buyer.api(path)
    buyer.call('/api/account/profile', expected=401)
    username = 'compose_' + uuid.uuid4().hex[:10]
    email = username + '@example.test'
    buyer.call('/auth/register', 'POST', {'_csrf': buyer.csrf(), 'username': username,
               'email': email, 'password': 'demo-password-123', 'confirm': 'demo-password-123'},
               form=True, expected=302)
    mail = Client(args.mail)
    message_id = None
    for _ in range(20):
        messages = mail.api('/api/v1/messages')['messages']
        message_id = next((m['ID'] for m in messages if any(
            address['Address'] == email for address in m['To'])), None)
        if message_id:
            break
        time.sleep(.5)
    assert message_id, 'Registration OTP not received by local Mailpit'
    message = mail.api('/api/v1/message/' + message_id)
    otp = re.search(r'\b[0-9]{6}\b', message['Text'])
    assert otp, 'OTP absent from local test email'
    buyer.call('/auth/activate', 'POST', {'_csrf': buyer.csrf(), 'email': email,
               'code': otp.group()}, form=True, expected=302)
    buyer.login(username)
    buyer.call('/api/admin/users', expected=403)
    buyer.api('/api/account/profile', 'PUT', {'displayName': 'Compose Acceptance', 'phone': '0901234567'})
    address = buyer.api('/api/account/addresses', 'POST', {
        'label': 'Test', 'recipientName': 'Compose Acceptance', 'phone': '0901234567',
        'line1': '12 Duong A', 'ward': 'Phuong A', 'district': 'Thu Duc',
        'city': 'Ho Chi Minh', 'defaultAddress': True}, expected=201)
    seller = Client(args.base)
    seller.login('reader')
    seller.call('/api/seller/shops/2/copies', expected=403)
    copy = seller.api('/api/seller/shops/1/copies', 'POST', {
        'productId': 1, 'condition': 'GOOD', 'defects': '', 'price': '80000',
        'reason': 'Compose acceptance fixture'}, expected=201)
    update = {'version': 0, 'condition': 'GOOD', 'defects': '', 'price': '80000',
              'status': 'AVAILABLE', 'reason': 'Compose acceptance sale'}
    seller.api(f"/api/seller/shops/1/copies/{copy['id']}", 'PUT', update, expected=204)
    seller.call(f"/api/seller/shops/1/copies/{copy['id']}", 'PUT', update, expected=409)
    buyer.api('/api/cart/items', 'POST', {'copyId': copy['id']})
    quote = buyer.api('/api/checkout/quote?carrierId=1&voucherCode=GOCSACH10')
    assert quote['total'] == 90000 and quote['discount'] == 10000, 'Quote calculation mismatch'
    checkout = {'addressId': address['id'], 'idempotencyKey': 'compose-' + uuid.uuid4().hex,
                'quoteFingerprint': quote['fingerprint'], 'paymentMethod': 'COD',
                'carrierId': 1, 'voucherCode': 'GOCSACH10'}
    orders = buyer.api('/api/checkout', 'POST', checkout)
    assert buyer.api('/api/checkout', 'POST', checkout) == orders, 'Checkout is not idempotent'
    order_id = orders[0]['id']
    for status in ['CONFIRMED', 'SHIPPING', 'COMPLETED']:
        seller.api(f'/api/seller/orders/{order_id}/status', 'POST', {'status': status}, expected=204)
    buyer.api(f'/api/orders/{order_id}/return', 'POST', {'reason': 'Compose test: book has missing pages.'})
    endpoint = f'/api/seller/orders/{order_id}/return/resolve'
    seller.api(endpoint, 'POST', {'status': 'APPROVED', 'note': 'Compose return accepted'}, expected=204)
    resolution = {'status': 'REFUNDED', 'note': 'Simulated refund for acceptance only',
                  'reference': 'COMPOSE-FAKE-' + str(order_id)}
    seller.call(endpoint, 'POST', resolution, expected=400)
    resolution['settled'] = True
    seller.api(endpoint, 'POST', resolution, expected=204)
    assert buyer.api(f'/api/orders/{order_id}/return')['status'] == 'REFUNDED'
    args.result.parent.mkdir(parents=True, exist_ok=True)
    args.result.write_text(json.dumps({'username': username, 'orderId': order_id,
        'copyId': copy['id'], 'result': 'PASS', 'realPayment': False}, indent=2), encoding='utf-8')
    print('PASS: public catalog, OTP email/activation, JWT, permissions, profile/address, inventory version,')
    print('COD checkout/voucher/shipping/retry, delivery, return and simulated manual refund on PostgreSQL.')


if __name__ == '__main__':
    main()
