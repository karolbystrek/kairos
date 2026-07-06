My engineering thesis topic:
"The goal of this project is to create a standalone notification system for restaurants, acting as a virtual paging device. In the proposed solution, the customer scans the code assigned to the order, which launches a dedicated mobile application or its browser version. The software maintains asynchronous communication with the user's device, providing notifications when the order is ready for pickup. The system provides an interface for restaurant staff to manually manage the queue, allowing it to function independently. Additionally, an open interface is provided, allowing external point-of-sale systems (POS) to automatically control order statuses."

More detailed project plan:

1. Business Context and Justification of the Problem
Traditional queue management systems in restaurants rely on physical paging devices, which generate high hardware costs and are prone to failure. Solutions based on dedicated mobile applications for specific chains encounter resistance from users who do not want to install software for a one-time transaction. The project aims to create a digital alternative operating in a SaaS model that minimizes the barrier to entry for the end customer while maintaining high communication reliability.

2. Main Objective
Design and implement a multi-user distributed system enabling asynchronous communication between the store staff and customers. The system will operate based on the immediate launch of the customer interface via QR codes.

3. Functional Scope and System Modules
The solution architecture will consist of three main components:

Client Application: A dedicated mobile application or lightweight browser interface launched after scanning the unique QR code of an order. It will monitor status changes using the WebSocket protocol and restore the status in the event of an overload or temporary connection loss.

Administrative Panel:
A dedicated application (e.g., web-based for tablets) for staff to manually manage the order lifecycle and generate QR codes. This module will ensure full system autonomy.

Open API: Design of a secure programming interface based on the REST architecture and a webhook mechanism. This will enable optional integration of the solution with external POS systems, automating status flow and data synchronization.

4. Usage Scenario
The scenario begins when a customer places an order at the checkout, after which they scan a generated QR code using their phone's camera. This action immediately launches a dedicated mobile app, if the customer has one installed, or a lightweight web app in their phone's browser. As an additional feature, an App Clip can be integrated on iOS devices, completely eliminating the need to download and install any software. The order number and its initial status are displayed on the screen, indicating that the meal is being prepared. During this time, the customer can lock the screen or use other apps, as the system asynchronously maintains a connection session in the background. When the meal is ready, the restaurant employee changes its status – this can be done using a dedicated panel in my system on a tablet or via an external POS system, if the restaurant has opted for such integration via a shared API. This action instantly triggers a notification that updates the interface on the customer's device to a ready-to-pick status, signaling it with vibration or sound. Once the meal is served, the staff marks the order as picked up, automatically closing the communication session and permanently deactivating the used QR code, preventing its reuse.